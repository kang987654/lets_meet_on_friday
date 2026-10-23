"""exp58c — exp58b 채택 후보(ROLE 역할 줄 + RES1 검색 결과 문구)의 마지막 확인 (0.38.6 M4).

1) 오버헤드: 첫 턴 token_count ×2 (exp56b 방식) + tokenize 차이 — 웹 검색 켠 판(앱 기본 측정 조건과 같게 전체 툴).
2) 웹 검색 켬: exp51b 스모크 12(위키 포함) + 사실 질문 4 — ROLE 이 search_wikipedia 호출을 줄이지 않는지.
3) 메모가 하나도 없을 때의 결과("저장된 기억이 하나도 없습니다…") — EMPTY0(앱 원문) vs EMPTY1(대화 속 답 허용), 앞 대화에 답 6 + 앞 대화 없음 4.
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

SYS = K.fixture("system_instruction.txt")
REM = K.fixture("turn_reminder.txt").strip()
ROLE_OLD = "You are a personal assistant named Kosmos\n"
ROLE_NEW = "You are a personal assistant named Kosmos who also chats, recommends and gives advice from your own knowledge\n"
assert ROLE_OLD in SYS
VARIANTS = {"base": SYS, "ROLE": SYS.replace(ROLE_OLD, ROLE_NEW)}

engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                    max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)

print(f"## tokenize ROLE +{len(engine.tokenize(VARIANTS['ROLE'])) - len(engine.tokenize(SYS))}", flush=True)
for v, s in VARIANTS.items():
    counts = []
    for _ in range(2):
        with K.create_chat_conversation(engine, system_message=s) as conv:
            conv.send_message(f"{REM}\n\n안녕")
            counts.append(conv.token_count)
    print(f"## token_count {v}: {counts}", flush=True)

FACT = ["에스파가 뭐야?", "블랙홀이 뭐야? 찾아봐", "세종대왕이 누구야?", "광합성이 뭐야?"]
for v, s in VARIANTS.items():
    ok = 0
    for tag, u, check in S.CASES:
        with K.create_chat_conversation(engine, system_message=s) as conv:
            res = conv.send_message(f"{REM}\n\n{u}")
        good = check(S.first_call(res))
        ok += good
        if not good:
            print(f"[smoke-on {v}] 실패 {tag}: {S.first_call(res)}", flush=True)
    wiki = 0
    for u in FACT:
        with K.create_chat_conversation(engine, system_message=s) as conv:
            call = S.first_call(conv.send_message(f"{REM}\n\n{u}"))
        wiki += bool(call) and call["name"] == "search_wikipedia"
        print(f"[fact {v}] {call and call['name']} | {u}", flush=True)
    print(f"== 웹 켬 {v}: smoke {ok}/{len(S.CASES)}, 사실 질문 위키 {wiki}/{len(FACT)}", flush=True)

EMPTY = {
    "EMPTY0": "저장된 기억이 하나도 없습니다. 사용자에게 저장된 것이 없다고 답하세요. 추측하지 마세요.",
    "EMPTY1": "저장된 기억이 하나도 없습니다. 사용자가 이 대화에서 이미 말한 내용이 있으면 그것으로 답하고, 없으면 저장된 것이 없다고 답하세요. 추측하지 마세요.",
}
HIST = [
    ("다음 주 수요일 회의실은 3층 B룸이야", "3층 B룸이요, 알겠어요.", "회의실 어디라고 했지?", "B룸"),
    ("택배 송장번호 6043 1122 9087이야", "송장번호 6043 1122 9087 확인했어요.", "송장번호 다시 알려줘", "6043"),
    ("점심은 12시 반에 먹기로 했어", "12시 반 점심이군요.", "점심 몇 시라고 했지?", "12시 반"),
    ("엄마 생신 선물로 스카프 사기로 했어", "스카프 좋은 선택이에요.", "엄마 선물 뭐로 하기로 했더라?", "스카프"),
    ("내일 만날 친구 이름은 지훈이야", "지훈 님이군요.", "내일 만나는 친구 이름이 뭐였지?", "지훈"),
    ("주차는 지하 2층 C-14에 했어", "지하 2층 C-14, 기억해 둘게요.", "나 어디 주차했다고 했지?", "C-14"),
]
RECALL = [("회사 주차 자리 어디라고 했지?", r"\d"), ("전에 말한 치과 이름 뭐였더라?", r"치과(는|가) \S+(이에요|예요)"),
          ("내가 알려준 와이파이 비번 뭐야?", r"[a-z]+\d+"), ("엄마 생신 날짜 기억나?", r"\d+월 ?\d+일")]
NEG = re.compile(r"(없어요|없습니다|없네요|모르겠|찾지 못|찾을 수 없|기억이 안)")


def answer(conv, q, empty):
    res = conv.send_message(f"{REM}\n\n{q}")
    tools = []
    for _ in range(3):
        cs = res.get("tool_calls") or []
        if not cs:
            break
        name = cs[0]["function"]["name"]
        tools.append(name)
        body = {"status": "success", "data": EMPTY[empty]} if name == "search_memory" else {"status": "success", "message": "완료"}
        res = conv.send_message(K.tool_response_message(name, json.dumps(body, ensure_ascii=False)))
    c = res.get("content")
    text = c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))
    return text.strip(), tools


TOOLS_OFF = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]
WIKI_LINE = next(l for l in SYS.splitlines() if "search_wikipedia" in l) + "\n"
for v, s in VARIANTS.items():
    s_off = s.replace(WIKI_LINE, "")
    for e in EMPTY:
        right, invented = 0, 0
        for u0, a0, q, key in HIST:
            with K.create_chat_conversation(engine, history=[{"role": "user", "content": u0}, {"role": "model", "content": a0}], tools=TOOLS_OFF, system_message=s_off) as conv:
                out, tools = answer(conv, q, e)
            right += key in out
            print(f"[empty-hist {v} {e}] {'맞음' if key in out else '틀림'} {','.join(tools)} | {q} → {out.replace(chr(10), ' / ')[:70]}", flush=True)
        for q, pat in RECALL:
            with K.create_chat_conversation(engine, tools=TOOLS_OFF, system_message=s_off) as conv:
                out, tools = answer(conv, q, e)
            inv = bool(re.search(pat, out)) and not NEG.search(out)
            invented += inv
            print(f"[empty-recall {v} {e}] {'지어냄 ' if inv else ''}| {q} → {out.replace(chr(10), ' / ')[:70]}", flush=True)
        print(f"== 메모 없음 {v} {e}: 대화 속 답 {right}/{len(HIST)}, 지어냄 {invented}/{len(RECALL)}", flush=True)
