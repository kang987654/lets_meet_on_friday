"""exp62c — 0.38.8 M2 다른 접근: 기억 검색 결과 맨 위에 지금 대화의 사용자 말을 그대로 싣는다.

exp62b: 안내 문구만으로는 방금 값을 먼저 답함 최고 2/4(T2), "예전 기록" 강조(T3·T4)는 0/4 — 문구 해석에 들쭉날쭉.
앱이 지금 대화(현재 에피소드)의 사용자 말 중 질의 낱말이 겹치는 것을 찾아 "- (방금 이 대화) <문장>"으로 결과 맨 위에 둔다(코드, 결정적).
  R1 그 줄 + 현행 끝 안내
  R2 R1 + 머리 안내 "(방금 이 대화)는 가장 최신 — 다르면 그 값을 답하고 다른 기록이 있었다고 덧붙이기"
회귀: weak(관계없는 결과 + 앞 대화 답 — 앞 대화 사용자 말이 그 줄로 들어간다), hit·episode·conflict(지금 대화에 관련 말 없음 → 줄 없음, exp62b 와 같다).
"""
import sys

import exp62_trust_order as X

sys.argv.append("--v=NONE")  # exp62b 는 가져올 때 측정을 돌린다 — 변형을 비워 세트만 쓴다.
import exp62b_new_value as B  # noqa: E402

M = X.M
R2_HEAD = "(방금 이 대화)는 사용자가 지금 대화에서 한 말이라 가장 최신입니다. 다른 기록과 값이 다르면 그 값을 답하고, 다른 기록이 있었다고 덧붙이세요.\n"


def body(variant, said, rest):
    line = f"- (방금 이 대화) {said}\n" if said else ""
    head = R2_HEAD if variant == "R2" and said else ""
    return head + line + rest + X.HIT_TAIL


for v in ("R1", "R2"):
    row = {}
    new = old = 0
    for said, q, key, line, old_key in X.NEWVAL:
        out, _ = X.turn([M("user", said), M("model", X.MEMO_REJECTED)], q, X.SYS, X.REM, search_body=body(v, said, line))
        first = key in out and (old_key not in out or out.index(key) < out.index(old_key))
        new += first
        old += old_key in out and key not in out
        print(f"[C {v}] {'새 값 먼저' if first else ''}{' 옛 값만' if old_key in out and key not in out else ''} | {q} → {out.replace(chr(10), ' / ')[:90]}", flush=True)
    row["C 방금 값 먼저"] = f"{new}/{len(X.NEWVAL)}"
    row["C 옛 값만"] = f"{old}/{len(X.NEWVAL)}"
    ok = 0
    for u0, a0, q, key in B.WEAK:
        out, _ = X.turn([M("user", u0), M("model", a0)], q, X.SYS, X.REM, search_body=body(v, u0, B.UNRELATED))
        ok += key in out
    row["weak"] = f"{ok}/{len(B.WEAK)}"
    print(f"== {v}: " + " | ".join(f"{k} {x}" for k, x in row.items()), flush=True)

e = X.engine()
print(f"## 토큰 R2 머리 +{len(e.tokenize(R2_HEAD))}, 줄 머리표 +{len(e.tokenize('- (방금 이 대화) '))}", flush=True)
