"""exp69b — 0.39.3 M4c: 실기기에서는 새 안내가 실렸는데도 134(2/2). 실제 결과를 그대로 넣어 재현하고 차이 ①②를 가른다.
① "(방금 이 대화)" 줄에 값 없는 지난 질문("…뭐였지?")도 실린다 ② "(과거 대화) N건에는 다른 값: 5, 214, 6043…" 무관한 숫자 잡음.
V0 실기기 그대로 · V1 질문 줄 빼기 · V2 잡음 줄 빼기 · V3 둘 다.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

VAL = "- (방금 이 대화) 내 자전거 비밀번호 4821이야 기억해줘\n"
QLINE = "- (방금 이 대화) 내가 알려준 자전거 비밀번호 뭐였지?\n"
GUIDE = R.CURRENT_FIRST_GUIDE + "\n"
MEMO = "- 자전거 비밀번호는 134 [비밀번호, 자전거]"
NOISE = "\n- (과거 대화) 2건에는 다른 값이 적혀 있습니다: 5, 214, 6043, 1122, 9087)"
V = {"V0": VAL + QLINE + GUIDE + MEMO + NOISE, "V1": VAL + GUIDE + MEMO + NOISE, "V2": VAL + QLINE + GUIDE + MEMO, "V3": VAL + GUIDE + MEMO}
H = [R.M("user", "내가 알려준 자전거 비밀번호 뭐였지?"), R.M("model", "자전거 비밀번호는 134예요. 혹시 다른 비밀번호를 말씀하신 건가요? 😊"),
     R.M("user", "내 자전거 비밀번호 4821이야 기억해줘"), R.M("model", R.MEMO_REJECTED),
     R.M("user", "30분 뒤에 물 마시라고 알려줘"), R.M("model", "알림을 등록하지 않았어요.")]
for name, b in V.items():
    for hname, h in (("앞 134 답 있음", H), ("앞 134 답 없음", H[2:])):
        res = []
        for _ in range(2):
            ans, _, _, _ = R.turn(h, "내가 알려준 자전거 비밀번호 뭐였지?", search_body=b + R.HIT_TAIL)
            res.append("4821" if "4821" in ans and "134예요" not in ans else ans[:30])
        print(f"{name} [{hname}] {res}", flush=True)
