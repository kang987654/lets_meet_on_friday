"""exp61 — 0.38.7 M5: 앞 대화에 말해 둔 숫자를 "다시 알려줘"로 되물으면 add_memory 가 불리는 오호출(exp58c "송장번호 다시 알려줘").

세트(웹 검색 끈 판):
  neg  앞 대화에 사용자가 정보를 말함(비서는 확인만) → 되묻기 — add_memory 를 부르면 실패, 답에 그 값이 있으면 맞힘
  pos  저장 요청(명시·암시) — add_memory 를 불러야
  smoke exp51b 툴 선택(위키 제외)
변형(add_memory 트리거 줄): base(현행) · REP(끝에 "되묻기는 저장 요청이 아님") · NEW("shares a new fact")
사용: python exp61_memory_repeat.py [--v=base,REP,NEW]
"""
import json
import sys

import litert_lm as llm
import kosmos_lab as K
import exp51b_smoke as S

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

VSEL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--v=")), "base,REP,NEW").split(",")
SYS_ON = K.fixture("system_instruction.txt")
WIKI = next(l for l in SYS_ON.splitlines() if "search_wikipedia" in l) + "\n"
SYS = SYS_ON.replace(WIKI, "")
TOOLS = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]
REM = K.fixture("turn_reminder.txt").strip()

LINE = next(l for l in SYS.splitlines() if "add_memory`." in l and "MUST call `add_memory`" in l)
VARIANTS = {
    "base": SYS,
    "REP": SYS.replace(LINE, LINE + ' Asking to repeat something ("다시 알려줘") is not a request to save.'),
    "NEW": SYS.replace(LINE, LINE.replace("shares a fact/preference/password to keep", "shares a new fact/preference/password to keep")),
    "SM": SYS.replace('"뭐였지", "뭐라고 했지",', '"뭐였지", "뭐라고 했지", "다시 알려줘",'),
    "V5": SYS.replace(LINE, LINE.replace("- The user asks you to remember", '- Never call `add_memory` when the user asks you to repeat something ("다시 알려줘"). The user asks you to remember')),
}
assert VARIANTS["SM"] != SYS and VARIANTS["V5"] != SYS

NEG = [  # (사용자, 비서, 되묻기, 값)
    ("택배 송장번호 6043 1122 9087이야", "송장번호 6043 1122 9087 확인했어요.", "송장번호 다시 알려줘", "6043"),
    ("내 사물함 번호는 214번이야", "사물함 214번이군요.", "사물함 번호 다시 말해 줘", "214"),
    ("회의는 오후 4시 30분에 시작해", "오후 4시 30분 회의군요.", "회의 몇 시라고 했지? 다시 알려 줘", "4시 30분"),
    ("주차는 지하 2층 C-14에 했어", "지하 2층 C-14군요.", "주차 위치 다시 알려줘", "C-14"),
    ("새 와이파이 비번은 sky7788이야", "sky7788이군요.", "아까 그 비번 다시 말해줘", "sky7788"),
    ("치과 예약 번호는 A-302야", "A-302 번호군요.", "예약 번호 뭐였지? 다시 알려줘", "A-302"),
]
POS = [
    ("나 땅콩 알레르기 있어, 잊지 마", "땅콩"), ("내 자전거 비밀번호 4821이야 기억해줘", "4821"),
    ("나는 아메리카노보다 라떼를 좋아해, 기억해 둬", "라떼"), ("우리 집 와이파이 비번은 kosmos2024야 저장해줘", "kosmos2024"),
    ("내 생일은 3월 5일이야", "3월 5일"), ("회사 사번은 K-20391이야 메모해줘", "K-20391"),
]

engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                    max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
for v in VSEL:
    print(f"## 토큰 {v}: +{len(engine.tokenize(VARIANTS[v])) - len(engine.tokenize(SYS))}", flush=True)


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


summary = {}
for v in VSEL:
    sysm = VARIANTS[v]
    wrong = right = 0
    for u0, a0, q, key in NEG:
        with K.create_chat_conversation(engine, history=[{"role": "user", "content": u0}, {"role": "model", "content": a0}], tools=TOOLS, system_message=sysm) as conv:
            res = conv.send_message(f"{REM}\n\n{q}")
            tools = []
            for _ in range(3):
                cs = res.get("tool_calls") or []
                if not cs:
                    break
                name = cs[0]["function"]["name"]
                tools.append(name)
                body = {"status": "success", "message": "기억에 저장했어요."} if name == "add_memory" else {"status": "success", "data": "'" + q + "' 로는 일치하는 기억이 없습니다.\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요."}
                res = conv.send_message(K.tool_response_message(name, json.dumps(body, ensure_ascii=False)))
            out = text_of(res).strip()
        wrong += "add_memory" in tools
        right += key in out
        print(f"[neg {v}] {'오호출 ' if 'add_memory' in tools else ''}{'맞음' if key in out else '틀림'} {','.join(tools)} | {q} → {out.replace(chr(10), ' / ')[:70]}", flush=True)
    ok = 0
    for u, key in POS:
        with K.create_chat_conversation(engine, tools=TOOLS, system_message=sysm) as conv:
            cs = conv.send_message(f"{REM}\n\n{u}").get("tool_calls") or []
        hit = bool(cs) and cs[0]["function"]["name"] == "add_memory" and key in str(cs[0]["function"].get("arguments", {}))
        ok += hit
        if not hit:
            print(f"[pos {v}] 놓침 {cs[:1]} | {u}", flush=True)
    sm = 0
    cases = [c for c in S.CASES if c[0] != "위키"]
    for tag, u, check in cases:
        with K.create_chat_conversation(engine, tools=TOOLS, system_message=sysm) as conv:
            good = check(S.first_call(conv.send_message(f"{REM}\n\n{u}")))
        sm += good
        if not good:
            print(f"[smoke {v}] 실패 {tag}", flush=True)
    summary[v] = f"neg 오호출 {wrong}/{len(NEG)} · 값 맞힘 {right}/{len(NEG)} | pos 저장 {ok}/{len(POS)} | smoke {sm}/{len(cases)}"
    print(f"== {v}: {summary[v]}", flush=True)

print("\n## 요약")
for v, s in summary.items():
    print(f"{v}: {s}", flush=True)
