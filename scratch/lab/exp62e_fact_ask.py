"""exp62e — 0.38.8 M3: 저장 낱말 없이 사실만 말하면 확인 + "메모해 둘까요?" (결정 1).

현행(exp62 B): 거짓 약속 1/8 · 저장 호출 5/8 · 물어봄 0/8, 앞에서 거절한 기록 뒤 "택배 송장번호 …이야" → "저장·관리 기능은 없어요".
후보(add_memory 트리거 줄):
  F1 "or shares a fact/preference/password to keep" 를 빼고 끝에 사실만 말하면 확인 + "메모해 둘까요?", 기억하겠다고 말하지 말 것
  F2 같은 뜻 짧게
지표: fact 8 — 물어봄·거짓 약속·저장 호출, yes — "메모해 둘까요?" 뒤 "응" → add_memory(값 포함), pos 저장 요청 6, drift — 앞 거절 기록 뒤 송장번호, smoke 11.
"""
import sys

import exp62_trust_order as X
import exp51b_smoke as S
import kosmos_lab as K

VSEL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--v=")), "base,F1,F2").split(",")
LINE = next(l for l in X.SYS.splitlines() if "MUST call `add_memory`" in l)
OLD = "The user asks you to remember something, or shares a fact/preference/password to keep:"
assert OLD in LINE
F1 = LINE.replace(OLD, "The user asks you to remember something:") + \
    ' If the user only shares a fact, confirm it and ask "메모해 둘까요?" — never say you will remember it.'
F2 = LINE.replace(OLD, "The user asks you to remember something:") + ' If they only share a fact, confirm it and ask "메모해 둘까요?".'
VARIANTS = {"base": X.SYS, "F1": X.SYS.replace(LINE, F1), "F2": X.SYS.replace(LINE, F2)}

YES = [  # (사실, 그 값)
    ("택배 송장번호 6043 1122 9087이야", "6043"), ("내 사물함 번호는 214번이야", "214"),
    ("우리 강아지 이름은 콩이야", "콩이"), ("새 와이파이 비번은 sky7788이야", "sky7788"),
]
MEMPOS = [("나 땅콩 알레르기 있어, 잊지 마", "땅콩"), ("내 자전거 비밀번호 4821이야 기억해줘", "4821"),
          ("나는 아메리카노보다 라떼를 좋아해, 기억해 둬", "라떼"), ("우리 집 와이파이 비번은 kosmos2024야 저장해줘", "kosmos2024"),
          ("회사 사번은 K-20391이야 메모해줘", "K-20391"), ("내 생일은 3월 5일이야 기억해", "3월 5일")]

e = X.engine()
for v in VSEL:
    print(f"## 토큰 {v}: +{len(e.tokenize(VARIANTS[v])) - len(e.tokenize(X.SYS))}", flush=True)

for v in VSEL:
    sysm = VARIANTS[v]
    row = X.run_B(sysm, X.REM, tag=v)
    ok = 0
    for fact, key in YES:
        hist = [X.M("user", fact), X.M("model", f"{fact.rstrip('이야').rstrip('야')} 확인했어요. 메모해 둘까요?")]
        with K.create_chat_conversation(e, history=hist, tools=X.TOOLS, system_message=sysm) as conv:
            cs = conv.send_message(f"{X.REM}\n\n응").get("tool_calls") or []
        hit = bool(cs) and cs[0]["function"]["name"] == "add_memory" and key in str(cs[0]["function"].get("arguments", {}))
        ok += hit
        if not hit:
            print(f"[yes {v}] 놓침 {cs[:1]} | {fact}", flush=True)
    row["응 → 저장"] = f"{ok}/{len(YES)}"
    ok = 0
    for u, key in MEMPOS:
        with K.create_chat_conversation(e, tools=X.TOOLS, system_message=sysm) as conv:
            cs = conv.send_message(f"{X.REM}\n\n{u}").get("tool_calls") or []
        ok += bool(cs) and cs[0]["function"]["name"] == "add_memory" and key in str(cs[0]["function"].get("arguments", {}))
    row["저장 요청"] = f"{ok}/{len(MEMPOS)}"
    out, tools = X.turn(X.DRIFT_HIST["refused"], "택배 송장번호 6043 1122 9087이야", sysm, X.REM)
    row["앞 거절 뒤 송장번호"] = "거절" if X.REFUSE.search(out) else ("물어봄" if X.ASK_SAVE.search(out) else "기타")
    print(f"[drift {v}] {','.join(tools)} | → {out.replace(chr(10), ' / ')[:90]}", flush=True)
    ok = 0
    cases = [c for c in S.CASES if c[0] != "위키"]
    for tag, u, check in cases:
        with K.create_chat_conversation(e, tools=X.TOOLS, system_message=sysm) as conv:
            good = check(S.first_call(conv.send_message(f"{X.REM}\n\n{u}")))
        ok += good
        if not good:
            print(f"[smoke {v}] 실패 {tag}", flush=True)
    row["smoke"] = f"{ok}/{len(cases)}"
    print(f"== {v}: " + " | ".join(f"{k} {x}" for k, x in row.items()), flush=True)
