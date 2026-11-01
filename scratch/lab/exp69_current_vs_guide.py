"""exp69 — 0.39.3 M4c: 기억 검색 결과의 "(방금 이 대화)" 줄과 메모·과거 대화 충돌 안내(MIXED_SOURCE_GUIDE)가 겹칠 때.

실기기(0.39.3 M4): 저장 시트를 거절한 직후 "내가 알려준 자전거 비밀번호 뭐였지?" → 방금 말한 4821 대신 메모 134. 실제 결과(활동 기록):
  - (방금 이 대화) 내 자전거 비밀번호 4821이야 기억해줘
  직접 저장한 메모는 … 둘이 다르면 메모의 값을 답하고, …          ← 0.36.1 충돌 안내
  - 자전거 비밀번호는 134 [비밀번호, 자전거]
  - (과거 대화) 2건에는 다른 값이 적혀 있습니다: …
회귀 세트 흉내(newval)에는 충돌 안내가 없어서 드러나지 않았다.
조건(방금 줄이 있을 때만 다르다): A 지금 · B 순서를 말하는 안내 · C 안내 빼기 · D 방금 줄을 집는 안내 · E B 안내를 맨 앞(방금 줄은 그 아래).
사용: python exp69_current_vs_guide.py [조건들, 기본 ABC]
장면: S1 방금 새 값(정답=방금 값) · S3 방금 줄이 값 없는 예전 질문 + 메모 vs 과거 대화 충돌(정답=메모 값 — 충돌 안내의 원래 목적).
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

GUIDE_A = "직접 저장한 메모는 사용자가 확인해 남긴 기록이고 (과거 대화)는 요약입니다. 둘이 다르면 메모의 값을 답하고, 과거 대화에 다른 값이 있었다고 덧붙이세요."
GUIDE_B = "값이 서로 다르면 (방금 이 대화) > 직접 저장한 메모 > (과거 대화) 순으로 믿고 답하고, 다른 값이 있었다고 덧붙이세요."
GUIDE_D = "(방금 이 대화)는 사용자가 방금 말한 가장 최신 값입니다. 메모·과거 대화와 다르면 그 값을 답하고, 예전 값이 있었다고 덧붙이세요."
TAIL = R.HIT_TAIL
CONDS = sys.argv[1] if len(sys.argv) > 1 else "ABC"


def body(head, cond, lines):
    if cond == "E" and head:  # B 안내를 맨 앞, 그 아래 방금 줄
        return GUIDE_B + "\n" + head + "\n".join(lines) + TAIL
    guide = {"A": GUIDE_A, "B": GUIDE_B if head else GUIDE_A, "C": None if head else GUIDE_A,
             "D": GUIDE_D if head else GUIDE_A, "E": GUIDE_A}[cond]
    return head + (guide + "\n" if guide else "") + "\n".join(lines) + TAIL


# (말한 새 값 문장, 메모 줄, 과거 대화 줄, 질문들, 정답, 오답)
S1 = [
    ("내 자전거 비밀번호 4821이야 기억해줘", "- 자전거 비밀번호는 134 [비밀번호, 자전거]", "- (과거 대화) 2건에는 다른 값이 적혀 있습니다: 4321)",
     ["내가 알려준 자전거 비밀번호 뭐였지?", "자전거 비밀번호 알려줘", "아까 말한 자전거 비번 뭐더라"], "4821", "134"),
    ("다음 주 금요일 회의실은 5층 C룸이야, 메모해줘", "- 회의실은 3층 B룸 [회의실]", "- (과거 대화) 회의실 위치 이야기: 사용자가 회의실이 3층 B룸이라고 했어요. [회의실]",
     ["회의실 어디라고 했지?", "회의실 위치 알려줘", "아까 말한 회의실 어디야?"], "5층", "3층"),
    ("사물함 번호 바뀌었어, 이제 215번이야 저장해줘", "- 사물함 번호는 214번 [사물함]", "- (과거 대화) 1건에는 다른 값이 적혀 있습니다: 213)",
     ["사물함 번호 뭐였지?", "내 사물함 몇 번이야?", "아까 말한 사물함 번호 알려줘"], "215", "214"),
]
S3 = [  # 방금 줄 = 값 없는 예전 질문, 메모가 정답
    ("자전거 비밀번호 뭐였더라", "- 자전거 비밀번호는 134 [비밀번호, 자전거]", "- (과거 대화) 자전거 이야기: 사용자가 자전거 비밀번호가 4321이라고 했어요. [자전거]",
     "그래서 자전거 비밀번호가 몇 번이야?", "134", "4321"),
    ("회의실 어디였지", "- 회의실은 3층 B룸 [회의실]", "- (과거 대화) 회의 준비: 사용자가 회의실이 7층 A룸이라고 했어요. [회의실]",
     "그래서 회의실이 어디야?", "3층", "7층"),
]

score = {c: [0, 0] for c in CONDS}
for cond in CONDS:
    for said, memo, past, qs, right, wrong in S1:
        hist = [R.M("user", said), R.M("model", R.MEMO_REJECTED), R.M("user", "30분 뒤에 물 마시라고 알려줘"), R.M("model", "알림을 등록하지 않았어요.")]
        b = body(f"- (방금 이 대화) {said}\n", cond, [memo, past])
        for q in qs:
            ans, _, _, _ = R.turn(hist, q, search_body=b)
            ok = right in ans.replace(" ", "") and wrong not in ans.replace(" ", "")
            score[cond][0] += ok
            if not ok:
                print(f"  [{cond} S1 실패] {q} | {ans[:60]}", flush=True)
    for said, memo, past, q, right, wrong in S3:
        hist = [R.M("user", said), R.M("model", "메모를 찾아볼게요."), R.M("user", "아 잠깐, 점심 메뉴 추천해 줘"), R.M("model", "김치찌개 어떠세요?")]
        b = body(f"- (방금 이 대화) {said}\n", cond, [memo, past])
        ans, _, _, _ = R.turn(hist, q, search_body=b)
        ok = right in ans.replace(" ", "") and not ans.replace(" ", "").startswith(wrong)
        score[cond][1] += ok
        if not ok:
            print(f"  [{cond} S3 실패] {q} | {ans[:60]}", flush=True)
    print(f"== {cond}: S1 {score[cond][0]}/9 · S3 {score[cond][1]}/{len(S3)}", flush=True)
print("안내 토큰 A", len(R.engine().tokenize(GUIDE_A)), "· B", len(R.engine().tokenize(GUIDE_B)), "· D", len(R.engine().tokenize(GUIDE_D)), flush=True)
