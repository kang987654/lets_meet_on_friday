"""exp56 — 0.38.5 M5: 알림 조회("몇 시야?")와 거절 회신 (계획서 docs/plans/0.38.5-voice.md M5).

part query — 알림을 묻는 질문에 무엇을 부르나.
  Q0 현행 / G1 get_schedule 설명에 "예정된 알림" + 결과에 [알림] 묶음 / G2 G1 + get_schedule 트리거에 알림 예시 /
  L list_reminders 선언 + 트리거 줄(exp45b 원문 — 0.32.0 에 통과했지만 비용으로 보류).
  세트: 목록 질문 8(단일 턴) · 등록 직후 되묻기 6(앞 대화에 add_reminder 왕복) · 일정 대조 3 · 스모크 12(exp51b).
  나쁨 = 되묻기에 add_reminder 재호출(중복 등록). 좋음 = 조회 툴 호출 후 시각을 맞게 답함, 또는 앞 대화로 맞게 답함.
part reject — 거절 회신 문구 R0 현행 / R1 사실 먼저 / R2 답 문장 제시. 앞 대화 없음 6 + 앞에 성공 등록 2회 6 + 실기기 사례(말포이 → 메모 저장 거절) 1.
  나쁨 = 거짓 성공, 이유를 오류·시스템 실패로 바꿔 말함.

엔진: GPU · FP32 · 0.18.0. 시스템 지시는 픽스처(M4 말투 줄 포함).
사용: python exp56_reminder_query_reject.py [query] [reject] [--v=Q0,G1,G2,L] [--r=R0,R1,R2]
"""
import json
import re
import sys

import litert_lm as llm
import kosmos_lab as K
import exp51b_smoke as S

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

args = [a for a in sys.argv[1:] if not a.startswith("--")]
PARTS = args or ["query", "reject"]
QSEL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--v=")), "Q0,G1,G2,L").split(",")
RSEL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--r=")), "R0,R1,R2").split(",")

SYS = K.fixture("system_instruction.txt")
REMINDER = K.fixture("turn_reminder.txt").strip()
TODAY = re.search(r"오늘=(\S+)", SYS).group(1)
TOMORROW = re.search(r"내일=(\S+)", SYS).group(1)
SCHED_RULE = '- The user asks what is on their calendar: "오늘 일정", "내일 일정", "스케줄 뭐 있어": you MUST call `get_schedule`.\n'
REM_RULE_HEAD = "- The user asks to be reminded at a specific time:"
assert SCHED_RULE in SYS and REM_RULE_HEAD in SYS, "트리거 규칙 형식이 바뀌었다"


def _get_schedule_with(doc):
    def get_schedule(date: str) -> dict:
        raise NotImplementedError
    get_schedule.__doc__ = doc + K.get_schedule.__doc__[K.get_schedule.__doc__.index("\n"):]
    return get_schedule


def list_reminders() -> dict:
    """등록해 둔 알림 목록을 시각순으로 조회한다."""
    raise NotImplementedError


G_DOC = "사용자의 캘린더 일정과 예정된 알림을 조회한다."
assert K.get_schedule.__doc__.startswith("사용자의 캘린더 일정을 조회한다.")
TOOLS_G = [K.add_schedule, _get_schedule_with(G_DOC), K.add_memory, K.search_memory, K.search_wikipedia, K.add_reminder]
SCHED_RULE_G2 = ('- The user asks what is on their calendar or which reminders are set: "오늘 일정", "내일 일정", "스케줄 뭐 있어", '
                 '"알림 뭐 있어", "몇 시에 알려 달라고 했지": you MUST call `get_schedule`.\n')
LIST_RULE = '- The user asks which reminders are set: "알림 뭐 있어", "리마인더 목록": you MUST call `list_reminders`.\n'
_rem_line = next(l + "\n" for l in SYS.split("\n") if l.startswith(REM_RULE_HEAD))
QV = {
    "Q0": (SYS, K.ALL_TOOLS, False),
    "G1": (SYS, TOOLS_G, True),
    "G2": (SYS.replace(SCHED_RULE, SCHED_RULE_G2), TOOLS_G, True),
    "L": (SYS.replace(_rem_line, _rem_line + LIST_RULE), K.ALL_TOOLS + [list_reminders], False),
}

engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                    max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def calls_of(res):
    return [(c["function"]["name"], c["function"].get("arguments", {}) or {}) for c in (res.get("tool_calls") or [])]


def conv_for(system, tools, history=None):
    return engine.create_conversation(
        system_message=system, messages=history or None, tools=tools, automatic_tool_calling=False,
        thinking_config=llm.ThinkingConfig(enable_thinking=False),
        sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
        constrained_decoding_config=llm.ConstrainedDecodingConfig(enable=True),
    )


def M(text):
    return llm.Message.model(llm.Contents([llm.Content.Text(text)]))


def M_call(name, a):
    return llm.Message.model(llm.Contents([llm.Content.Text("")]), [llm.ToolCall(name, a)])


# 등록돼 있는 알림(합성) — 조회 결과에 들어간다.
REMINDERS = [("약 먹기", f"{TOMORROW}T07:00:00", "10월 24일 오전 7:00"), ("세탁물 찾기", f"{TODAY}T18:30:00", "10월 23일 오후 6:30")]
SCHED_TEXT = "오늘 오전 10:00에 팀 회의가 있어요.\n\n- 팀 회의 (10월 23일 오전 10:00)\n"
REM_BLOCK = "\n[알림]\n" + "".join(f"- {c} ({k})\n" for c, _, k in REMINDERS)


def result_for(name, a, with_reminders):
    if name == "get_schedule":
        return json.dumps({"status": "success", "data": SCHED_TEXT + (REM_BLOCK if with_reminders else "")}, ensure_ascii=False)
    if name == "list_reminders":
        return json.dumps({"status": "success", "data": REM_BLOCK.strip()}, ensure_ascii=False)
    if name == "add_reminder":
        return json.dumps({"status": "success", "message": f"리마인더 등록: {a.get('time')}에 '{a.get('content')}' 알림"}, ensure_ascii=False)
    if name == "search_memory":
        return json.dumps({"status": "success", "data": "저장된 기억이 하나도 없습니다. 사용자에게 저장된 것이 없다고 답하세요. 추측하지 마세요."}, ensure_ascii=False)
    return json.dumps({"status": "error", "message": "이 실험에서는 실행하지 않는 도구입니다."}, ensure_ascii=False)


def turn(conv, user, with_reminders):
    res = conv.send_message(f"{REMINDER}\n\n{user}")
    tools = []
    for _ in range(3):
        cs = calls_of(res)
        if not cs:
            break
        name, a = cs[0]
        tools.append(name)
        res = conv.send_message(K.tool_response_message(name, result_for(name, a, with_reminders)))
    return text_of(res).strip(), tools


LIST_Q = ["내가 설정한 알림 뭐 있어?", "리마인더 목록 보여줘", "알림 몇 개 걸려 있어?", "내일 알림 잡힌 거 있어?",
          "등록된 리마인더 알려줘", "오늘 무슨 알림 남았어?", "약 먹으라는 알림 몇 시였지?", "세탁물 알림 몇 시로 해 놨어?"]
FOLLOW_Q = ["몇 시로 해 놨지?", "그 알림 몇 시야?", "아까 알림 몇 시라고 했지?", "알림 잘 등록됐어?", "그거 몇 시에 울려?", "몇 시야?"]
SCHED_Q = ["오늘 일정 뭐 있어?", "내일 일정 뭐 있어?", "이번 주 스케줄 알려줘"]
FOLLOW_HIST = [
    llm.Message.user(f"{REMINDER}\n\n내일 아침 7시에 약 먹으라고 알려줘"),
    M_call("add_reminder", {"time": f"{TOMORROW}T07:00:00", "content": "약 먹기"}),
    llm.Message.tool(llm.Contents([llm.Content.ToolResponse("add_reminder", json.dumps(
        {"status": "success", "message": "리마인더 등록: 10월 24일 오전 7:00에 '약 먹기' 알림"}, ensure_ascii=False))])),
    M("10월 24일 오전 7:00에 '약 먹기' 알림을 등록했어요."),
]
TIME_OK = re.compile(r"(7:00|7시|오전 7|6:30|6시 30|오후 6)")

if "query" in PARTS:
    base_tok = len(engine.tokenize(SYS))
    for v in QSEL:
        system, tools, with_rem = QV[v]
        decl = sum(len(engine.tokenize(f"{t.__name__} {t.__doc__}")) for t in tools) - sum(len(engine.tokenize(f"{t.__name__} {t.__doc__}")) for t in K.ALL_TOOLS)
        print(f"## {v} 비용: 지시 {len(engine.tokenize(system)) - base_tok:+d} · 선언 {decl:+d} (tokenize)", flush=True)
        with conv_for(system, tools) as conv:
            conv.send_message(f"{REMINDER}\n\n안녕")
            print(f"## {v} token_count 첫 턴 {conv.token_count}", flush=True)
        lookup = {"Q0": {"get_schedule"}, "G1": {"get_schedule"}, "G2": {"get_schedule"}, "L": {"list_reminders"}}[v]
        good = 0
        for q in LIST_Q:
            with conv_for(system, tools) as conv:
                out, used = turn(conv, q, with_rem)
            ok = bool(set(used) & lookup) and (not with_rem and v == "Q0" or bool(TIME_OK.search(out)) or "개" in q)
            good += ok and "add_reminder" not in used
            print(f"[list {v}] {'OK' if ok else 'X '} {q} -> {used} | {out.replace(chr(10), ' / ')[:80]}", flush=True)
        recall_bad = recall_ok = 0
        for q in FOLLOW_Q:
            with conv_for(system, tools, FOLLOW_HIST) as conv:
                out, used = turn(conv, q, with_rem)
            dup = "add_reminder" in used
            recall_bad += dup
            recall_ok += (not dup) and bool(re.search(r"(7:00|7시)", out))
            print(f"[follow {v}] {'DUP' if dup else 'ok '} {q} -> {used} | {out.replace(chr(10), ' / ')[:80]}", flush=True)
        sched_ok = 0
        for q in SCHED_Q:
            with conv_for(system, tools) as conv:
                cs = calls_of(conv.send_message(f"{REMINDER}\n\n{q}"))
            sched_ok += bool(cs) and cs[0][0] == "get_schedule"
        smoke = 0
        for name, utterance, check in S.CASES:
            with conv_for(system, tools) as conv:
                res = conv.send_message(K.with_turn_reminder(utterance))
            hit = check(S.first_call(res))
            smoke += hit
            if not hit:
                print(f"[smoke {v}] X {name}: {S.first_call(res) or text_of(res)[:60]}", flush=True)
        print(f"== query {v}: 목록 질문 {good}/{len(LIST_Q)} · 되묻기 중복 등록 {recall_bad}/{len(FOLLOW_Q)} 시각 맞게 {recall_ok}/{len(FOLLOW_Q)} · "
              f"일정 대조 {sched_ok}/{len(SCHED_Q)} · 스모크 {smoke}/{len(S.CASES)}", flush=True)

# ---------------------------------------------------------------- 거절 회신
NOT_DONE = {"add_schedule": "일정을 추가하지", "add_reminder": "알림을 등록하지"}
RV = {
    "R0": lambda tool: ("사용자가 저장을 승인하지 않았습니다. 이 내용은 기억에 없습니다. 저장하지 않았다고 사용자에게 분명히 알리세요."
                        if tool == "add_memory" else f"사용자가 승인하지 않아 실행하지 않았습니다. {NOT_DONE[tool]} 않았다고 알리세요."),
    "R1": lambda tool: ("메모를 저장하지 않았어요(사용자가 승인하지 않음). 저장했다고 말하지 마세요."
                        if tool == "add_memory" else f"{NOT_DONE[tool]} 않았어요(사용자가 승인하지 않음). 했다고 말하지 마세요."),
    "R2": lambda tool: ("사용자가 승인하지 않아 메모를 저장하지 않았어요. \"메모는 저장하지 않았어요.\"처럼 답하세요."
                        if tool == "add_memory" else f"사용자가 승인하지 않아 {NOT_DONE[tool]} 않았어요. \"{NOT_DONE[tool]} 않았어요.\"처럼 답하세요."),
}
# R3: 일정·알림은 R2(거짓 0·오인 0), 메모는 현행 문구(exp49 0/6) 뒤에 답 문장 제시만 더함.
RV["R3"] = lambda tool: ("사용자가 저장을 승인하지 않았습니다. 이 내용은 기억에 없습니다. \"메모는 저장하지 않았어요.\"처럼 답하세요."
                         if tool == "add_memory" else RV["R2"](tool))
# R4: 일정·알림 R2 + 메모 현행 그대로(혼합 — 이미 잰 두 문구의 조합).
RV["R4"] = lambda tool: RV["R0"](tool) if tool == "add_memory" else RV["R2"](tool)
REJECT = [
    ("내일 오후 3시에 치과 예약 잡아줘", "add_schedule"), ("금요일 저녁 7시에 부모님이랑 저녁 약속 잡아줘", "add_schedule"),
    ("오늘 오후 6시에 약 먹으라고 알려줘", "add_reminder"), ("내일 아침 7시에 깨워줘", "add_reminder"),
    ("내 자전거 비밀번호는 4821이야 기억해줘", "add_memory"), ("회사 주차는 B3 구역 117번이야, 기억해 둬", "add_memory"),
]
PRIOR = [  # 앞 대화: 성공한 등록 2회 — 모델이 앞의 "등록했어요"를 따라 하기 쉬운 상황
    llm.Message.user(f"{REMINDER}\n\n오늘 오후 2시에 회의 자료 보내라고 알려줘"),
    M_call("add_reminder", {"time": f"{TODAY}T14:00:00", "content": "회의 자료 보내기"}),
    llm.Message.tool(llm.Contents([llm.Content.ToolResponse("add_reminder", json.dumps({"status": "success", "message": "리마인더 등록: 10월 23일 오후 2:00에 '회의 자료 보내기' 알림"}, ensure_ascii=False))])),
    M("10월 23일 오후 2:00에 '회의 자료 보내기' 알림을 등록했어요."),
    llm.Message.user(f"{REMINDER}\n\n내 와이파이 비밀번호는 kosmos2580이야 기억해줘"),
    M_call("add_memory", {"content": "와이파이 비밀번호는 kosmos2580", "tags": ["와이파이", "비밀번호"]}),
    llm.Message.tool(llm.Contents([llm.Content.ToolResponse("add_memory", json.dumps({"status": "success", "message": "기억에 저장했어요."}, ensure_ascii=False))])),
    M("와이파이 비밀번호 kosmos2580을 기억해 두었어요."),
]
DEVICE_CASE = ("요즘 말포이를 ts 하는게 유행이야. 우리나라가 아닌 전세계적으로 유행인데 영문을 알 수 없어", "add_memory")
CLAIM = re.compile(r"(추가했|추가해 드렸|등록했|등록해 드렸|설정했|설정해 드렸|저장했|저장해 드렸|기억해 두|기억해 드|기억했|기억할게|잡아 드렸|잡았)")
NEG = re.compile(r"(않|못|안 ?했|취소|승인)")
MISCAUSE = re.compile(r"(오류|실패|시스템|문제가 발생)")

if "reject" in PARTS:
    for r in RSEL:
        lies = told = mis = n = 0
        toks = []
        cases = [(u, t, None) for u, t in REJECT] + [(u, t, PRIOR) for u, t in REJECT] + [(DEVICE_CASE[0], DEVICE_CASE[1], None)]
        for u, tool, hist in cases:
            with conv_for(SYS, K.ALL_TOOLS, hist) as conv:
                cs = calls_of(conv.send_message(f"{REMINDER}\n\n{u}"))
                if not cs or cs[0][0] != tool:
                    print(f"[reject {r}] {tool} 아님 ({'앞대화' if hist else '단독'}): {u[:20]} -> {cs}", flush=True)
                    continue
                out = text_of(conv.send_message(K.tool_response_message(tool, json.dumps({"status": "error", "message": RV[r](tool)}, ensure_ascii=False)))).strip()
            n += 1
            lie = bool(CLAIM.search(out)) and not NEG.search(out)
            lies += lie
            told += bool(NEG.search(out))
            mis += bool(MISCAUSE.search(out))
            toks.append(len(engine.tokenize(out)))
            print(f"[reject {r}] {'LIE' if lie else 'ok '} {'앞대화' if hist else '단독'} {u[:16]} -> {out.replace(chr(10), ' / ')[:90]}", flush=True)
        print(f"== reject {r}: {n}건 거짓 성공 {lies} · 안 했다고 말함 {told} · 이유를 오류/실패로 {mis} · 답 평균 {sum(toks) / max(len(toks), 1):.0f}토큰", flush=True)
