"""exp91 — 반복 질문 처방 A 검증 (0.41.15 M1).

새 규칙(run.py repeated_drop = 앱 RepeatedQuestions): 앞 같은 질문과 지금 질문 사이에 질문이 아닌 사용자 말이 있을 때만 앞 질문·답을 뺀다.
  [A] exp90 에서 일반 거절·기억 상실이 난 맥락(S2·S6·S7)을 새 규칙 / 옛 규칙(항상 뺌) / 빼지 않음으로 — 7문항 × 2회.
  [B] 0.39.3 이 막으려던 경우: 질문 → 틀린 옛 값 답 → "아니야, 새 값이야" → 같은 질문. 새 규칙은 빼야 하고 새 값으로 답해야 한다.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import exp90_repeat_refusal as E  # noqa: E402
import run as R  # noqa: E402


def old_drop(history):
    """0.39.3~0.41.14 규칙 — 사이 말과 무관하게 항상 뺀다."""
    at = max((i for i, m in enumerate(history) if m["role"] == "user"), default=-1)
    drop = set()
    for i in range(max(at, 0)):
        if history[i]["role"] == "user" and R.same_question(history[i]["content"], history[at]["content"]):
            drop.add(i)
            if i + 1 < at and history[i + 1]["role"] == "model":
                drop.add(i + 1)
    return [m for i, m in enumerate(history) if i not in drop]


def hist_for(raw, q, cond):
    full = list(raw) + [R.M("user", q)]
    base = E.drop_refusal_only(full)
    out = {"new": R.repeated_drop(base), "old": old_drop(base), "keep": base}[cond]
    return out[:-1] if out and out[-1]["role"] == "user" and out[-1]["content"] == q else out


def tally(summary, key, calls, ok, ref):
    b = summary.setdefault(key, {"n": 0, "search": 0, "ok": 0, "noTool": 0})
    b["n"] += 1
    b["search"] += "search_memory" in calls
    b["ok"] += ok
    b["noTool"] += (not calls) and ref


def part_a():
    summary = {}
    prior_ai = [R.M("user", "내 주민등록번호 뭐야?"), R.M("model", "저는 AI 비서라서 사용자의 민감한 개인 정보를 알 수 없습니다.")]
    jobs = []
    for tag, fact_u, q, key, memo in E.ITEMS:
        for s_name, raw, m_line, _ in E.scenario_histories(fact_u, q, key, memo):
            if s_name == "S2_rejected_chat":
                jobs.append((tag, s_name, raw, q, key, m_line))
    for tag, q, key, memo, ok_ans in E.PRESENT_ITEMS:
        jobs.append((tag, "S6_present", [R.M("user", q), R.M("model", ok_ans)], q, key, memo))
        jobs.append((tag, "S7_after_ai_refusal", prior_ai + [R.M("user", q), R.M("model", ok_ans)], q, key, memo))
    print("=== [A] exp90 실패 맥락 — new(처방 A) / old(항상 뺌) / keep(안 뺌) ===", flush=True)
    for tag, s_name, raw, q, key, m_line in jobs:
        for cond in ("new", "old", "keep"):
            h = hist_for(raw, q, cond)
            for rep in (1, 2):
                calls, ans, ref_r, ref_ai = E.run_query_turn(h, raw, q, m_line)
                ok = key.replace(" ", "") in ans.replace(" ", "")
                tally(summary, (s_name, cond), calls, ok, ref_r or ref_ai)
                print(f"  [{tag}|{s_name}|{cond}#r{rep}] roles={''.join(m['role'][0].upper() for m in h) or 'EMPTY'} calls={calls} ok={ok} | {ans[:60]!r}", flush=True)
    print("\n=== [A 종합] ===", flush=True)
    for (s_name, cond), b in sorted(summary.items()):
        print(f"  {s_name:22} {cond:5} search={b['search']}/{b['n']} 정답={b['ok']}/{b['n']} 무호출거절={b['noTool']}/{b['n']}", flush=True)


CHANGED = [
    ("자전거 비밀번호 뭐야?", "자전거 비밀번호는 134예요.", "아니야, 내 자전거 비밀번호는 4821이야", "4821", "134", "- 자전거 자물쇠 비밀번호는 134 [비밀번호, 자전거]"),
    ("주차 어디에 했지?", "지하 2층 C-14에 주차했어요.", "아니 오늘 주차는 지하 3층 D-07에 했어", "D-07", "C-14", "- 주차 위치 지하 2층 C-14 [주차]"),
    ("와이파이 비번 뭐야?", "와이파이 비밀번호는 sky7788이에요.", "와이파이 비번 sky9900으로 바꿨어", "sky9900", "sky7788", "- 집 와이파이 비밀번호는 sky7788 [와이파이, 비밀번호]"),
]


def part_b():
    print("\n=== [B] 값이 바뀐 반복(0.39.3 목적) — new 는 빼야 하고 새 값으로 답해야 함 ===", flush=True)
    res = {}
    for q, wrong, said, new, old, old_line in CHANGED:
        raw = [R.M("user", q), R.M("model", wrong), R.M("user", said), R.M("model", "알겠어요.")]
        for cond in ("new", "old", "keep"):
            h = hist_for(raw, q, cond)
            for rep in (1, 2):
                calls, ans, _, _ = E.run_query_turn(h, raw, q, old_line)
                ok = new in ans and old not in ans
                b = res.setdefault(cond, [0, 0]); b[0] += ok; b[1] += 1
                print(f"  [{q}|{cond}#r{rep}] dropped={len(h) < len(raw)} calls={calls} ok={ok} | {ans[:60]!r}", flush=True)
    for cond, (ok, n) in res.items():
        print(f"  B {cond:5} 새 값 정답={ok}/{n}", flush=True)


if __name__ == "__main__":
    # [WHY] B 첫 판은 "아니야, 4821이야"처럼 검색어 없는 말이라 앱의 '(방금 이 대화)' 줄에 안 실려 세 조건 모두 0/6 — 0.39.3 원 문항처럼 검색어가 든 진술로.
    if "--b-only" not in sys.argv:
        part_a()
    part_b()
