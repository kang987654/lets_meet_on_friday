"""exp58b — exp58 후속(0.38.6 M4): 시스템 지시 한 줄로는 안 고쳐진 두 문제를 다른 표면으로.

exp58 결과: 툴 밖 요청 거절은 판정식이 좁아 2/12 로 셌지만 출력을 읽으면 base 8/12("죄송해요, 저는 ~ 추천해 드릴 수 없어요"),
ALLOW·REM·MEM·HIST 어느 한 줄도 줄이지 못함. 대화에 답이 있는 질문은 6/6 search_memory → "기억이 없어요"(맞힘 1/6), HIST 줄로도 2/6.

① 거절: 역할 줄(ROLE)·턴 리마인더 고쳐 쓰기(REM2, 대체라 토큰 거의 0)·둘 다. 판정 = "죄송|드릴 수 없|기능은 없|…" 넓게 + 출력 전부 기록.
② 대화 속 답: search_memory '없음' 결과 문구(턴 몫 — 고정 예산 밖)를 앱 원문(RES0)과 후보(RES1)로. 결과는 앱 실행기의 태그 폴백 판 그대로 —
   exp58 은 첫 줄만 흉내 내 앱보다 약하게 '없다'를 시켰다. RES1 이 앞 대화가 없을 때 지어내지 않는지(recall) 함께 본다.
사용: python exp58b_answer_surface.py [--v=base,ROLE,REM2,ROLE+REM2] [--part=casual,smoke,mempos,recall,hist]
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

VSEL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--v=")), "base,ROLE,REM2,ROLE+REM2").split(",")
PARTS = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--part=")), "casual,smoke,mempos,recall,hist").split(",")

SYS_ON = K.fixture("system_instruction.txt")
WIKI_LINE = next(l for l in SYS_ON.splitlines() if "search_wikipedia" in l) + "\n"
SYS = SYS_ON.replace(WIKI_LINE, "")
TOOLS = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]
REMINDER = K.fixture("turn_reminder.txt").strip()

ROLE_OLD = "You are a personal assistant named Kosmos\n"
REM_OLD = "For THIS request, first decide if one of your tools applies. If it does, you MUST call the tool — do not answer from memory or merely promise."
assert ROLE_OLD in SYS and REM_OLD in REMINDER

EDITS = {
    "ROLE": ("sys", ROLE_OLD, "You are a personal assistant named Kosmos who also chats, recommends and gives advice from your own knowledge\n"),
    "REM2": ("rem", REM_OLD, "If one of your tools fits THIS request, you MUST call it — do not merely promise. Otherwise answer it yourself."),
}


def build(variant):
    sysm, rem = SYS, REMINDER
    for key in variant.split("+"):
        if key == "base":
            continue
        where, old, new = EDITS[key]
        if where == "sys":
            sysm = sysm.replace(old, new)
        else:
            rem = rem.replace(old, new)
    return sysm, rem


# 앱 SearchMemoryToolExecutor 태그 폴백 판(메모가 있으나 낱말이 안 맞을 때) — 저장된 메모 3건은 질문과 무관한 것.
TAGS = "비밀번호, 자전거, 건강, 선호도"
NOTES = "- 자전거 비밀번호는 4821 [비밀번호, 자전거]\n- 땅콩 알레르기 있음 [건강]\n- 아메리카노보다 라떼를 좋아함 [선호도]"
RES_TAIL = {
    "RES0": "\n이 목록에 질문의 답이 없으면 없다고 답하세요. 절대 지어내지 마세요.",
    "RES1": "\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요. 거기에도 없으면 없다고 답하세요. 절대 지어내지 마세요.",
}


def miss(keyword, res):
    return (f"'{keyword}' 로는 일치하는 기억이 없습니다. 저장된 기억에 붙은 분류는 다음과 같습니다: {TAGS}.\n"
            "이 중 질문에 해당할 만한 분류가 있으면 그 단어로 `search_memory` 를 한 번 더 호출하세요. 해당하는 분류가 없으면 그런 기억이 없다고 답하세요.\n"
            f"가장 최근에 저장된 기억:\n{NOTES}{RES_TAIL[res]}")


engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                    max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
for v in VSEL:
    s, r = build(v)
    print(f"## 토큰 {v}: 시스템 +{len(engine.tokenize(s)) - len(engine.tokenize(SYS))}, 리마인더 +{len(engine.tokenize(r)) - len(engine.tokenize(REMINDER))}", flush=True)
print(f"## 토큰 RES1-RES0: +{len(engine.tokenize(RES_TAIL['RES1'])) - len(engine.tokenize(RES_TAIL['RES0']))}", flush=True)


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def calls_of(res):
    return [(c["function"]["name"], c["function"].get("arguments", {}) or {}) for c in (res.get("tool_calls") or [])]


def tool_result(name, a, res_variant):
    if name == "search_memory":
        return {"status": "success", "data": miss(a.get("keyword", ""), res_variant)}
    if name == "add_memory":
        return {"status": "success", "message": "기억에 저장했어요."}
    if name == "get_schedule":
        return {"status": "success", "data": "등록된 일정이 없어요."}
    if name == "list_reminders":
        return {"status": "success", "data": "등록된 알림이 없어요."}
    return {"status": "success", "message": "등록했어요."}


def run_turn(conv, rem, user_text, res_variant="RES0", rounds=3):
    res = conv.send_message(f"{rem}\n\n{user_text}")
    tools = []
    for _ in range(rounds):
        cs = calls_of(res)
        if not cs:
            break
        name, a = cs[0]
        tools.append(name)
        res = conv.send_message(K.tool_response_message(name, json.dumps(tool_result(name, a, res_variant), ensure_ascii=False)))
    return text_of(res).strip(), tools


CASUAL = [
    "점심 메뉴 추천해 줄래?", "비 오는 날 듣기 좋은 노래 추천해줘", "회의 때 긴장 안 하는 방법 알려줘", "주말에 뭐 하면 좋을까?",
    "영어로 '감사합니다' 어떻게 말해?", "간단한 저녁 메뉴 뭐가 좋을까", "아침 운동 루틴 짜줘", "김치찌개 끓이는 법 알려줘",
    "커피 하루에 몇 잔이 적당해?", "오늘 좀 피곤하다", "말포이 TS 유행 알아?", "친구 생일 선물 뭐가 좋을까?",
]
MEMPOS = [
    ("나 땅콩 알레르기 있어, 잊지 마", "땅콩"), ("내 자전거 비밀번호 4821이야 기억해줘", "4821"),
    ("나는 아메리카노보다 라떼를 좋아해, 기억해 둬", "라떼"), ("우리 집 와이파이 비번은 kosmos2024야 저장해줘", "kosmos2024"),
    ("내 생일은 3월 5일이야", "3월 5일"),
]
HIST = [
    ("다음 주 수요일 회의실은 3층 B룸이야", "3층 B룸이요, 알겠어요.", "회의실 어디라고 했지?", "B룸"),
    ("택배 송장번호 6043 1122 9087이야", "송장번호 6043 1122 9087 확인했어요.", "송장번호 다시 알려줘", "6043"),
    ("점심은 12시 반에 먹기로 했어", "12시 반 점심이군요.", "점심 몇 시라고 했지?", "12시 반"),
    ("엄마 생신 선물로 스카프 사기로 했어", "스카프 좋은 선택이에요.", "엄마 선물 뭐로 하기로 했더라?", "스카프"),
    ("내일 만날 친구 이름은 지훈이야", "지훈 님이군요.", "내일 만나는 친구 이름이 뭐였지?", "지훈"),
    ("주차는 지하 2층 C-14에 했어", "지하 2층 C-14, 기억해 둘게요.", "나 어디 주차했다고 했지?", "C-14"),
]
# 앞 대화 없음 + 저장된 메모에도 없는 것 — 답을 지어내면 안 된다(없다고 말해야).
RECALL = [("회사 주차 자리 어디라고 했지?", r"\d"), ("전에 말한 치과 이름 뭐였더라?", r"치과(는|가) \S+(이에요|예요)"),
          ("내가 알려준 와이파이 비번 뭐야?", r"[a-z]+\d+"), ("엄마 생신 날짜 기억나?", r"\d+월 ?\d+일")]
REFUSE = re.compile(r"(죄송|드릴 수 없|드릴 수는 없|기능은 없|기능이 없|도움을 드리기 어려|어렵습니다|가지고 있지 않|할 수 없|지원하지|제공하지)")
NEG = re.compile(r"(없어요|없습니다|없네요|모르겠|찾지 못|찾을 수 없|기억이 안)")

summary = {}
for v in VSEL:
    sysm, rem = build(v)
    row = {}
    if "casual" in PARTS:
        refused = 0
        for u in CASUAL:
            with K.create_chat_conversation(engine, tools=TOOLS, system_message=sysm) as conv:
                out, tools = run_turn(conv, rem, u)
            bad = bool(REFUSE.search(out))
            refused += bad
            print(f"[casual {v}] {'거절 ' if bad else ''}{'툴 ' + ','.join(tools) + ' ' if tools else ''}| {u} → {out.replace(chr(10), ' / ')[:90]}", flush=True)
        row["casual 거절"] = f"{refused}/{len(CASUAL)}"
    if "smoke" in PARTS:
        cases = [c for c in S.CASES if c[0] != "위키"]
        ok = 0
        for tag, u, check in cases:
            with K.create_chat_conversation(engine, tools=TOOLS, system_message=sysm) as conv:
                res = conv.send_message(f"{rem}\n\n{u}")
            good = check(S.first_call(res))
            ok += good
            if not good:
                print(f"[smoke {v}] 실패 {tag}: {S.first_call(res)}", flush=True)
        row["smoke"] = f"{ok}/{len(cases)}"
    if "mempos" in PARTS:
        ok = 0
        for u, key in MEMPOS:
            with K.create_chat_conversation(engine, tools=TOOLS, system_message=sysm) as conv:
                cs = calls_of(conv.send_message(f"{rem}\n\n{u}"))
            ok += bool(cs) and cs[0][0] == "add_memory" and key in str(cs[0][1].get("content", ""))
        row["mempos"] = f"{ok}/{len(MEMPOS)}"
    for resv in ("RES0", "RES1"):
        if "hist" in PARTS:
            called, right = 0, 0
            for u0, a0, q, key in HIST:
                hist = [{"role": "user", "content": u0}, {"role": "model", "content": a0}]
                with K.create_chat_conversation(engine, history=hist, tools=TOOLS, system_message=sysm) as conv:
                    out, tools = run_turn(conv, rem, q, resv)
                called += "search_memory" in tools
                right += key in out
                print(f"[hist {v} {resv}] {'검색 ' if 'search_memory' in tools else ''}{'맞음' if key in out else '틀림'} | {q} → {out.replace(chr(10), ' / ')[:70]}", flush=True)
            row[f"hist {resv} 맞힘"] = f"{right}/{len(HIST)}"
        if "recall" in PARTS:
            invented, said_none = 0, 0
            for q, pat in RECALL:
                with K.create_chat_conversation(engine, tools=TOOLS, system_message=sysm) as conv:
                    out, tools = run_turn(conv, rem, q, resv)
                inv = bool(re.search(pat, out)) and not NEG.search(out)
                invented += inv
                said_none += bool(NEG.search(out))
                print(f"[recall {v} {resv}] {'지어냄 ' if inv else ''}{','.join(tools)} | {q} → {out.replace(chr(10), ' / ')[:70]}", flush=True)
            row[f"recall {resv} 지어냄"] = f"{invented}/{len(RECALL)}"
            row[f"recall {resv} 없다"] = f"{said_none}/{len(RECALL)}"
    summary[v] = row
    print(f"== {v}: {row}", flush=True)

print("\n## 요약")
for v, row in summary.items():
    print(f"{v:>12}: " + " | ".join(f"{k} {x}" for k, x in row.items()), flush=True)
