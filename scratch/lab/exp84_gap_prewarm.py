"""exp84 — 0.41.9 M0: 오래 쉰 뒤(기록이 비고 요약 한 줄만 있을 때) 대화를 미리 읽어 둘 방법.

배경: 0.41.7 M4 부터 30분 넘게 쉬면 지난 대화를 모델 기록에서 빼고 `(과거 대화) 제목: 요약` 한 줄을 시스템 지시 끝에 둔다.
기록이 비면 미리 준비(`GemmaModelRunner.prepareConversation`)가 보낼 메시지가 없어 시스템 지시를 첫 질문이 읽는다(휴대폰 약 4.6초).
엔진 대화 API 에는 읽기만 하는 함수가 없다 → 무언가를 출력 1토큰으로 보내 KV 에 올려야 한다(앱의 미리 준비와 같은 방식, exp66d·e).

조건 (cold = 미리 읽지 않고 첫 send, warm = 앱처럼 [] 로 만들고 메시지를 상한 1로 보낸 뒤 첫 send):
- SYS   cold : 지금 앱 — 시스템 지시 끝 요약 줄, 기록 없음(비교 기준·4.6초 경로)
- EMPTY warm : ㄴ — SYS 와 같은 지시, 빈 model 메시지로 미리 읽기
- HM    cold/warm : ㄱ-model — 요약 줄을 기록 첫 model 메시지로(시스템 지시 끝에는 없음)
- HU    cold/warm : ㄱ-user  — 요약 줄을 기록 첫 user 메시지로
- BOTH  cold/warm : ㄱ-둘 다 — 시스템 지시 끝에도 두고 model 메시지로 한 번 더
판정은 exp81 과 같은 9건(간격 뒤 새 말 5 · 이어 말하기 4). 요약은 앱 요약 프롬프트로 이 모델이 만든다.
warm 이 cold 와 같은 첫 응답(글·툴 호출)을 내는지(=앱에서 미리 읽어도 답이 같은지)와 첫 질문의 프리필 토큰도 적는다.
실행: KOSMOS_LAB_SPEC=adaptive python exp84_gap_prewarm.py > exp84_out.txt 2> exp84_err.txt
"""
import json
import re
import sys
import time
from pathlib import Path

import litert_lm as llm

sys.path.insert(0, str(Path(__file__).parent))
import kosmos_lab as K  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")

U, M = "user", "model"

PW = [(U, "내 노트북 비밀번호 뭐였지?"),
      (M, "죄송해요, 저장된 기억을 확인해 봤는데 노트북 비밀번호에 대한 정보가 없어요. 비밀번호를 알려주시면 메모해 둘까요?")]
STEW = [(U, "김치찌개 끓이는 법 알려줘"),
        (M, "김치찌개는 이렇게 끓여요.\n1. 돼지고기와 신김치를 냄비에 넣고 볶아요.\n2. 물을 붓고 15분 정도 끓여요.\n3. 두부와 대파를 넣고 5분 더 끓이면 완성이에요.\n다른 찌개 레시피도 알려드릴까요?")]
MEETING = [(U, "회의 일정 하나 잡아줘"),
           (M, "좋아요. 어떤 날짜와 몇 시로 잡을까요? 회의 제목도 알려주세요.")]
BREAD = [(U, "사워도우 빵 만드는 과정 단계별로 알려줘"),
         (M, "사워도우는 세 단계로 만들어요.\n1단계 르방 준비: 발효종에 밀가루와 물을 먹여 4~6시간 활성화해요.\n"
             "2단계 오토리즈와 반죽: 밀가루와 물을 먼저 섞어 30분 쉬게 한 뒤(오토리즈) 르방과 소금을 넣고 접기를 반복해요.\n"
             "3단계 발효와 굽기: 냉장고에서 하룻밤 저온 발효한 뒤 250도 오븐에서 40분 구워요.")]
SUSHI = [(U, "어제 강남역 근처 '하루'라는 일식집 갔는데 좋더라"),
         (M, "강남역 '하루'에서 좋은 시간 보내셨군요! 어떤 메뉴가 좋았나요?"),
         (U, "연어덮밥이 맛있었어"),
         (M, "연어덮밥 좋죠! 다음에 가시면 다른 메뉴도 드셔 보세요.")]
BUSAN = [(U, "다음 주에 부산 2박 3일 여행 가는데 첫째 날 일정 짜줘"),
         (M, "첫째 날 일정이에요.\n- 오전: 해운대 해수욕장 산책\n- 점심: 해운대 시장에서 국밥\n- 오후: 동백섬 누리마루 둘러보기\n- 저녁: 광안리 해변에서 광안대교 야경")]
LOCKER = [(U, "내 사물함 번호는 214번이야"),
          (M, "사물함 번호가 214번이군요. 메모해 둘까요?"),
          (U, "아니 괜찮아"),
          (M, "알겠어요. 필요하면 언제든 말씀해 주세요.")]

CASES = [
    ("new", "pw-test", PW, "아아 테스트", ("avoid", ["비밀번호", "노트북"])),
    ("new", "pw-um", PW, "음…", ("avoid", ["비밀번호", "노트북"])),
    ("new", "stew-time", STEW, "지금 몇 시야?", ("avoid", ["찌개", "레시피", "김치"])),
    ("new", "meeting-test", MEETING, "테스트", ("avoid", ["회의"])),
    ("new", "pw-dentist", PW, "내일 오후 3시에 치과 예약 넣어줘", ("tool", "add_schedule", "치과", ["비밀번호", "노트북"])),
    ("cont", "bread-step2", BREAD, "아까 그거 두 번째 단계 다시 자세히 설명해줘", ("gold", ["오토리즈"])),
    ("cont", "sushi-name", SUSHI, "아까 말한 그 일식집 이름 뭐였지?", ("gold", ["하루"])),
    ("cont", "busan-day2", BUSAN, "그럼 둘째 날은?", ("gold", ["부산", "감천", "태종대", "자갈치", "남포", "송도", "영도", "해동용궁사", "기장", "흰여울"])),
    ("cont", "locker-num", LOCKER, "내 사물함 번호 몇 번이라고 했지?", ("gold", ["214"])),
]

EMPTY_MEMORY = "저장된 기억이 하나도 없습니다. 사용자가 이 대화에서 이미 말한 내용이 있으면 그것으로 답하고, 없으면 저장된 것이 없다고 답하세요. 추측하지 마세요."
SYS = K.fixture("system_instruction.txt")
SUMMARY_SYS = K.fixture("episode_summary_system.txt")

# (이름, 시스템 지시에 요약 줄, 기록 첫 메시지 역할 또는 None, 미리 읽기 메시지: "line"|"empty"|None)
CONDS = [
    ("SYS-cold", True, None, None),
    ("EMPTY-warm", True, None, "empty"),
    ("HM-cold", False, M, None),
    ("HM-warm", False, M, "line"),
    ("HU-cold", False, U, None),
    ("HU-warm", False, U, "line"),
    ("BOTH-cold", True, M, None),
    ("BOTH-warm", True, M, "line"),
]


def msg(role, text):
    return llm.Message.user(text) if role == U else llm.Message.model(llm.Contents([llm.Content.Text(text)]))


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def calls_of(res):
    out = []
    for call in res.get("tool_calls") or []:
        fn = call.get("function", call)
        args = fn.get("arguments")
        if isinstance(args, str):
            try:
                args = json.loads(args)
            except json.JSONDecodeError:
                args = {"raw": args}
        out.append((fn.get("name"), args or {}))
    return out


def summarize(engine, episode):
    transcript = "\n".join(f"{'사용자' if r == U else '비서'}: {t}" for r, t in episode)
    with engine.create_conversation(**K.speculative_kwargs(), system_message=SUMMARY_SYS,
                                    thinking_config=llm.ThinkingConfig(enable_thinking=False),
                                    sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0)) as conv:
        out = text_of(conv.send_message(f"다음 대화를 요약하세요.\n\n{transcript}"))
    title = re.search(r"제목:\s*(.+)", out)
    body = re.search(r"요약:\s*([\s\S]+?)(?:\n---|\Z)", out)
    return f"(과거 대화) {title.group(1).strip() if title else ''}: {body.group(1).strip().replace(chr(10), ' ') if body else out.strip()}"


def run(engine, cond, line, utterance, kind):
    _, in_sys, role, warm = cond
    system = SYS + "\n\n" + line if in_sys else SYS
    first_msg = [msg(role, line)] if role else []
    prewarm_err = ""
    if warm is None:
        conv = K.create_chat_conversation(engine, history=first_msg, system_message=system)
    else:
        conv = K.create_chat_conversation(engine, history=[], system_message=system)
        body = line if warm == "line" else ""
        try:
            conv.send_message(msg(role or M, body), max_output_tokens=1)
        except Exception as e:  # noqa: BLE001
            prewarm_err = f"미리읽기실패:{type(e).__name__}:{e}"
    before = conv.token_count
    t0 = time.perf_counter()
    res = conv.send_message(K.with_turn_reminder(utterance))
    first_sec = time.perf_counter() - t0
    after = conv.token_count
    first = (text_of(res), [(n, json.dumps(a, ensure_ascii=False, sort_keys=True)) for n, a in calls_of(res)])
    trace = []
    for _ in range(2):
        calls = calls_of(res)
        if not calls:
            break
        name, args = calls[0]
        trace.append(f"{name}({json.dumps(args, ensure_ascii=False)})")
        if name == "search_memory":
            body = EMPTY_MEMORY if kind == "new" else f"- {line}\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요."
            res = conv.send_message(K.tool_response_message(name, json.dumps({"status": "success", "data": body}, ensure_ascii=False)))
        else:
            break
    conv.close()
    return trace, text_of(res), first, before, after, first_sec, prewarm_err


def judge(rule, trace, answer):
    joined = " ".join(trace) + " " + answer
    if rule[0] == "avoid":
        hit = [w for w in rule[1] if w in joined]
        return not hit, f"앞주제낱말={hit}" if hit else "이어받지않음"
    if rule[0] == "tool":
        _, tool, arg_word, avoid = rule
        called = any(t.startswith(tool) and arg_word in t for t in trace)
        hit = [w for w in avoid if w in joined]
        return called and not hit, f"{tool}={'O' if called else 'X'} 앞주제낱말={hit}"
    found = [w for w in rule[1] if w in answer]
    return bool(found), f"정답낱말={found}"


def main():
    engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                        max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
    summaries = {}
    for _, name, ep, _, _ in CASES:
        if id(ep) not in summaries:
            summaries[id(ep)] = summarize(engine, ep)
            print(f"[요약] {name}: {summaries[id(ep)]}", flush=True)
    score, firsts, same = {}, {}, {}
    for kind, name, ep, utt, rule in CASES:
        line = summaries[id(ep)]
        for cond in CONDS:
            trace, answer, first, before, after, sec, err = run(engine, cond, line, utt, kind)
            ok, why = judge(rule, trace, answer)
            s = score.setdefault((kind, cond[0]), [0, 0])
            s[0] += ok
            s[1] += 1
            firsts[(name, cond[0])] = first
            print(f"[{kind}/{cond[0]}] {name} | {'통과' if ok else '실패'} | {why} | KV {before}→{after} 첫send {sec:.2f}s {err}"
                  f" | 툴={' ; '.join(trace) or '-'} | {answer.replace(chr(10), ' ')[:140]}", flush=True)
        for base in ("HM", "HU", "BOTH"):
            eq = firsts[(name, f"{base}-cold")] == firsts[(name, f"{base}-warm")]
            same.setdefault(base, [0, 0])
            same[base][0] += eq
            same[base][1] += 1
        eq = firsts[(name, "SYS-cold")] == firsts[(name, "EMPTY-warm")]
        same.setdefault("EMPTY(vs SYS)", [0, 0])
        same["EMPTY(vs SYS)"][0] += eq
        same["EMPTY(vs SYS)"][1] += 1
    print("\n## 판정", flush=True)
    for (kind, cond), (ok, n) in sorted(score.items()):
        print(f"{kind:5s} {cond:10s} {ok}/{n}", flush=True)
    print("\n## 미리 읽어도 첫 응답이 같은가(warm == cold)", flush=True)
    for base, (ok, n) in same.items():
        print(f"{base:14s} {ok}/{n}", flush=True)


if __name__ == "__main__":
    main()
