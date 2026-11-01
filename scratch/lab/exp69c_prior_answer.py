"""exp69c — 0.39.3 M4d: 모델이 앞에서 틀리게 한 답("134예요")이 기록에 있으면 새 안내가 있어도 그 답을 따라 한다(exp69b). 검색 결과 문구로 바로잡을 수 있는가.

기록: 같은 질문 → 틀린 답(옛 값) → 새 값 말함 → 저장 거절 → 알림 거절 → 같은 질문. 정답 = 새 값.
P0 지금(M4c) · P1 안내에 "앞에서 다른 값을 답했어도 고쳐서 답하세요." · P2 방금 줄을 결과 맨 끝으로(안내는 그 바로 위) · P3 P1+P2.
대조: 앞 답이 없는 기록(정답이 나와야 한다 — 무손상 확인).
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

G = R.CURRENT_FIRST_GUIDE
FIX = " 앞에서 다른 값을 답했어도 고쳐서 답하세요."
CASES = [  # (새 값 문장, 질문, 틀린 앞 답, 메모 줄, 과거 대화 줄, 새 값, 옛 값)
    ("내 자전거 비밀번호 4821이야 기억해줘", "내가 알려준 자전거 비밀번호 뭐였지?", "자전거 비밀번호는 134예요. 혹시 다른 비밀번호를 말씀하신 건가요? 😊",
     "- 자전거 비밀번호는 134 [비밀번호, 자전거]", "- (과거 대화) 2건에는 다른 값이 적혀 있습니다: 5, 214, 6043, 1122, 9087)", "4821", "134"),
    ("다음 주 금요일 회의실은 5층 C룸이야, 메모해줘", "회의실 어디라고 했지?", "회의실은 3층 B룸이라고 하셨어요. 😊",
     "- 회의실은 3층 B룸 [회의실]", "- (과거 대화) 회의실 위치 이야기: 사용자가 회의실이 3층 B룸이라고 했어요. [회의실]", "5층", "3층"),
    ("사물함 번호 바뀌었어, 이제 215번이야 저장해줘", "내 사물함 몇 번이야?", "사물함 번호는 214번이에요. 😊",
     "- 사물함 번호는 214번 [사물함]", "- (과거 대화) 1건에는 다른 값이 적혀 있습니다: 213)", "215", "214"),
]


def body(cond, said, q, memo, past):
    head = f"- (방금 이 대화) {said}\n- (방금 이 대화) {q}\n"
    guide = G + (FIX if cond in ("P1", "P3") else "")
    if cond in ("P2", "P3"):
        return f"{memo}\n{past}\n{guide}\n{head.rstrip()}" + R.HIT_TAIL
    return f"{head}{guide}\n{memo}\n{past}" + R.HIT_TAIL


conds = sys.argv[1] if len(sys.argv) > 1 else "P0,P1,P2,P3"
for cond in conds.split(","):
    ok_prior = ok_clean = 0
    for said, q, wrong, memo, past, new, old in CASES:
        tail = [R.M("user", said), R.M("model", R.MEMO_REJECTED), R.M("user", "30분 뒤에 물 마시라고 알려줘"), R.M("model", "알림을 등록하지 않았어요.")]
        for prior, h in ((True, [R.M("user", q), R.M("model", wrong)] + tail), (False, tail)):
            for _ in range(2):
                ans, _, _, _ = R.turn(h, q, search_body=body(cond, said, q, memo, past))
                a = ans.replace(" ", "")
                ok = new in a and not a.startswith(old) and f"{old}예요" not in a and f"{old}번이에요" not in a
                if prior:
                    ok_prior += ok
                else:
                    ok_clean += ok
                if not ok:
                    print(f"  [{cond} {'앞답' if prior else '깨끗'}] {q} | {ans[:55]}", flush=True)
    print(f"== {cond}: 앞 틀린 답 있음 {ok_prior}/6 · 없음 {ok_clean}/6", flush=True)
