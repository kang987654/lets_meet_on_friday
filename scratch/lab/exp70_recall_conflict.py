"""exp70 — 0.39.6 M1: RecallConflict 에서 다른 주제 문장의 숫자를 뺐을 때 모델 답변이 유지·개선되는지 실측.

실기기(0.39.3 M4c) 모양:
- 예전(잡음 있음): "- (과거 대화) 2건에는 다른 값이 적혀 있습니다: 5, 214, 6043, 1122, 9087)"
- 지금(관련 문장 값만): "- (과거 대화) 1건에는 다른 값이 적혀 있습니다: 4321)" 또는 무관 요약이면 줄 자체가 빠짐.

장면 1(메모 vs 과거 대화 충돌 — 메모 값 134 를 답하고 과거 대화 다른 값 4321 만 덧붙여야 함, 무관한 5/214/6043 언급 없어야 함).
장면 2(방금 대화 4821 vs 메모 134 vs 과거 대화 충돌 — 방금 값 4821 을 답해야 함).
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

MIXED_GUIDE = "직접 저장한 메모는 사용자가 확인해 남긴 기록이고 (과거 대화)는 요약입니다. 둘이 다르면 메모의 값을 답하고, 과거 대화에 다른 값이 있었다고 덧붙이세요."

NOISY_LINE = "- (과거 대화) 2건에는 다른 값이 적혀 있습니다: 5, 214, 6043, 1122, 9087, 4321)"
CLEAN_LINE = "- (과거 대화) 1건에는 다른 값이 적혀 있습니다: 4321)"

QS = ["내가 알려준 자전거 비밀번호 뭐였지?", "자전거 비밀번호 알려줘", "자전거 비번 몇 번이야?"]

for label, line in (("예전(잡음 포함)", NOISY_LINE), ("M1(주제 문장만)", CLEAN_LINE)):
    s1_ok = 0
    s1_no_noise = 0
    body1 = f"{MIXED_GUIDE}\n- 자전거 비밀번호는 134 [비밀번호, 자전거]\n{line}{R.HIT_TAIL}"
    for q in QS:
        ans, _, _, _ = R.turn(None, q, search_body=body1)
        a = ans.replace(" ", "")
        ok = "134" in a and not a.startswith("4321")
        no_noise = not any(n in a for n in ("214", "6043", "1122", "9087"))
        s1_ok += ok
        s1_no_noise += no_noise
        print(f"  [S1 {label}] {q} -> ok={ok} no_noise={no_noise} | {ans[:70]}", flush=True)

    s2_ok = 0
    said = "내 자전거 비밀번호 4821이야 기억해줘"
    h2 = [R.M("user", said), R.M("model", R.MEMO_REJECTED), R.M("user", "30분 뒤에 물 마시라고 알려줘"), R.M("model", "알림을 등록하지 않았어요.")]
    body2 = f"- (방금 이 대화) {said}\n{R.CURRENT_FIRST_GUIDE}\n- 자전거 비밀번호는 134 [비밀번호, 자전거]\n{line}{R.HIT_TAIL}"
    for q in QS:
        ans, _, _, _ = R.turn(h2, q, search_body=body2)
        a = ans.replace(" ", "")
        ok = "4821" in a and not a.startswith("134")
        s2_ok += ok
        print(f"  [S2 {label}] {q} -> ok={ok} | {ans[:70]}", flush=True)

    print(f"== {label}: S1 정답 {s1_ok}/3 (무관숫자 불언급 {s1_no_noise}/3) · S2 방금값 {s2_ok}/3", flush=True)
