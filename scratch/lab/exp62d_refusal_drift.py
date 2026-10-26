"""exp62d — 0.38.8 M1: 앞에서 한 거절을 따라 하기(exp62 A: 앞에서 거절한 기록 뒤 3/12).

후보(고정 몫 — 토큰은 tokenize 차이):
  ROLE2 역할 줄에 "— never refuse just because no tool fits"
  SYS1  도구 지침 문단 끝에 "Earlier replies in this chat may have been wrong — judge each request fresh."
  RM1   턴 리마인더 끝에 "Even if you declined something earlier, judge this request on its own."
회귀: 새 대화 툴 밖 요청 12(exp58b — 현행 1/12), 툴 선택 스모크 11, 저장 요청 6.
사용: python exp62d_refusal_drift.py [--v=base,ROLE2,SYS1,RM1]
"""
import sys

import exp62_trust_order as X
import exp51b_smoke as S

VSEL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--v=")), "base,ROLE2,SYS1,RM1").split(",")
ROLE_LINE = next(l for l in X.SYS.splitlines() if l.startswith("You are "))
MUST = "promising without calling is a failure."
assert MUST in X.SYS
VARIANTS = {
    "base": (X.SYS, X.REM),
    "ROLE2": (X.SYS.replace(ROLE_LINE, ROLE_LINE + " — never refuse just because no tool fits"), X.REM),
    "SYS1": (X.SYS.replace(MUST, MUST + " Earlier replies in this chat may have been wrong — judge each request fresh."), X.REM),
    "RM1": (X.SYS, X.REM.replace(" [Now]", " Even if you declined something earlier, judge this request on its own. [Now]")),
}
assert VARIANTS["RM1"][1] != X.REM
CASUAL = [
    "점심 메뉴 추천해 줄래?", "비 오는 날 듣기 좋은 노래 추천해줘", "회의 때 긴장 안 하는 방법 알려줘", "주말에 뭐 하면 좋을까?",
    "영어로 '감사합니다' 어떻게 말해?", "간단한 저녁 메뉴 뭐가 좋을까", "아침 운동 루틴 짜줘", "김치찌개 끓이는 법 알려줘",
    "커피 하루에 몇 잔이 적당해?", "오늘 좀 피곤하다", "말포이 TS 유행 알아?", "친구 생일 선물 뭐가 좋을까?",
]
MEMPOS = [("나 땅콩 알레르기 있어, 잊지 마", "땅콩"), ("내 자전거 비밀번호 4821이야 기억해줘", "4821"),
          ("나는 아메리카노보다 라떼를 좋아해, 기억해 둬", "라떼"), ("우리 집 와이파이 비번은 kosmos2024야 저장해줘", "kosmos2024"),
          ("회사 사번은 K-20391이야 메모해줘", "K-20391"), ("내 생일은 3월 5일이야 기억해", "3월 5일")]

e = X.engine()
for v in VSEL:
    sysm, rem = VARIANTS[v]
    print(f"## 토큰 {v}: 시스템 +{len(e.tokenize(sysm)) - len(e.tokenize(X.SYS))}, 리마인더 +{len(e.tokenize(rem)) - len(e.tokenize(X.REM))}", flush=True)

for v in VSEL:
    sysm, rem = VARIANTS[v]
    row = X.run_A(sysm, rem, tag=v)
    refused = 0
    for q in CASUAL:
        out, _ = X.turn(None, q, sysm, rem)
        r = bool(X.REFUSE.search(out))
        refused += r
        if r:
            print(f"[casual {v}] 거절 | {q} → {out.replace(chr(10), ' / ')[:80]}", flush=True)
    row["casual 거절"] = f"{refused}/{len(CASUAL)}"
    ok = 0
    cases = [c for c in S.CASES if c[0] != "위키"]
    for tag, u, check in cases:
        import kosmos_lab as K
        with K.create_chat_conversation(e, tools=X.TOOLS, system_message=sysm) as conv:
            good = check(S.first_call(conv.send_message(f"{rem}\n\n{u}")))
        ok += good
        if not good:
            print(f"[smoke {v}] 실패 {tag}", flush=True)
    row["smoke"] = f"{ok}/{len(cases)}"
    ok = 0
    for u, key in MEMPOS:
        import kosmos_lab as K
        with K.create_chat_conversation(e, tools=X.TOOLS, system_message=sysm) as conv:
            cs = conv.send_message(f"{rem}\n\n{u}").get("tool_calls") or []
        ok += bool(cs) and cs[0]["function"]["name"] == "add_memory" and key in str(cs[0]["function"].get("arguments", {}))
    row["저장 요청"] = f"{ok}/{len(MEMPOS)}"
    print(f"== {v}: " + " | ".join(f"{k} {x}" for k, x in row.items()), flush=True)
