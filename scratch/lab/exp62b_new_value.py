"""exp62b — 0.38.8 M2: 방금 대화의 새 값 > 메모 > 지난 대화 (결정 2). 기억 검색 결과 문구(턴 몫)만 바꾼다.

현행(T0) C 세트 방금 값 0/4·옛 값만 4/4(exp62). 후보:
  T1 끝 안내를 "방금 다른 값을 말했다면 그 값이 최신" 포함으로 교체
  T2 결과 맨 앞에 최신 우선 안내 + 현행 끝 안내
회귀(exp60 세트): hit 앞 대화 없이 관련 메모로 답 6, episode 무관한 앞 대화 + 관련 지난 대화 3, conflict 메모 vs 요약(MIXED) 4, weak 관계없는 결과 + 앞 대화 답 6.
"""
import exp62_trust_order as X

T0_TAIL = X.HIT_TAIL
T1_TAIL = "\n사용자가 이 대화에서 이 목록과 다른 값을 방금 말했다면 그 값이 가장 최신이니 그것을 답하세요. 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요."
T2_HEAD = "사용자가 이 대화에서 방금 말한 값이 있으면 그것이 가장 최신입니다. 아래 목록과 다르면 방금 값을 답하고, 기억에는 다른 값이 있었다고 덧붙이세요.\n"
T3_HEAD = "아래는 예전에 저장된 기록입니다. 사용자가 이 대화에서 다른 값을 말했다면 그 값이 더 최신이니 그 값으로 답하고, 예전 기록에는 다른 값이 있었다고 덧붙이세요.\n"
VARIANTS = {"T0": ("", T0_TAIL), "T1": ("", T1_TAIL), "T2": (T2_HEAD, T0_TAIL), "T3": (T3_HEAD, T0_TAIL), "T4": (T3_HEAD, T1_TAIL)}
import sys as _sys
_sel = next((a.split("=", 1)[1] for a in _sys.argv[1:] if a.startswith("--v=")), None)
if _sel:
    VARIANTS = {k: v for k, v in VARIANTS.items() if k in _sel.split(",")}

UNRELATED = "- (과거 대화) 사물함 비밀번호 설정 및 서점 이야기: 사용자가 사물함 비밀번호를 물었고 다음주 월요일 10시 팀 회의 일정을 추가해 달라고 했어요. [사물함, 비밀번호, 회의]"
WEAK = [
    ("다음 주 수요일 회의실은 3층 B룸이야", "3층 B룸이요, 알겠어요.", "회의실 어디라고 했지?", "B룸"),
    ("택배 송장번호 6043 1122 9087이야", "송장번호 6043 1122 9087 확인했어요.", "송장번호 다시 알려줘", "6043"),
    ("점심은 12시 반에 먹기로 했어", "12시 반 점심이군요.", "점심 몇 시라고 했지?", "12시 반"),
    ("엄마 생신 선물로 스카프 사기로 했어", "스카프 좋은 선택이에요.", "엄마 선물 뭐로 하기로 했더라?", "스카프"),
    ("내일 만날 친구 이름은 지훈이야", "지훈 님이군요.", "내일 만나는 친구 이름이 뭐였지?", "지훈"),
    ("주차는 지하 2층 C-14에 했어", "지하 2층 C-14, 기억해 둘게요.", "나 어디 주차했다고 했지?", "C-14"),
]
HIT = [
    ("내 자전거 비밀번호 뭐였지?", "- 자전거 자물쇠 비밀번호는 4821 [비밀번호, 자전거]", "4821"),
    ("회사 주차 자리 어디라고 했지?", "- 회사 주차는 B3 구역 117번 [주차, 회사]", "117"),
    ("내가 알려준 와이파이 비번 뭐야?", "- 집 와이파이 비밀번호는 kosmos2024 [와이파이, 비밀번호]", "kosmos2024"),
    ("엄마 생신 언제라고 했지?", "- 엄마 생신은 11월 7일 [가족, 생일]", "11월 7일"),
    ("내가 좋아한다고 한 커피 뭐였지?", "- 아메리카노보다 라떼를 좋아함 [선호도]", "라떼"),
    ("치과 이름 뭐였더라?", "- 치과는 강남역 근처 미소치과 [치과]", "미소치과"),
]
EPISODE = [
    ("오늘 날씨 좋다", "정말 좋은 날씨예요.", "지난번에 기타 레슨 몇 시로 바뀌었다고 했지?",
     "- (과거 대화) 기타 레슨 시간 변경: 사용자가 수요일 기타 레슨이 저녁 8시로 바뀌었다고 알려 주었어요. [기타, 레슨]", "8시"),
    ("배고프다", "뭐라도 드세요.", "전에 러닝 누구랑 한다고 했더라?",
     "- (과거 대화) 주말 러닝 계획: 사용자가 토요일 아침 한강에서 지훈이와 5km 뛰기로 했어요. [러닝, 한강]", "지훈"),
    ("고마워", "천만에요.", "여동생 생일 언제라고 했지?",
     "- (과거 대화) 가족 생일 이야기: 사용자가 여동생 생일이 5월 3일이라고 알려 주었어요. [생일, 여동생]", "5월 3일"),
]
CONFLICT = [
    ("자전거 비밀번호 뭐였지?", "- 자전거 비밀번호는 134 [자전거, 비밀번호]",
     "- (과거 대화) 자전거 비밀번호 변경: 사용자가 비밀번호를 4321로 바꿨다고 했어요. [자전거, 비밀번호]", "134"),
    ("와이파이 비번 뭐야?", "- 와이파이 비밀번호는 sky7788 [와이파이]",
     "- (과거 대화) 와이파이 이야기: 사용자가 와이파이 비밀번호가 sky1234라고 했어요. [와이파이]", "sky7788"),
    ("회사 주차 몇 번이라고 했지?", "- 회사 주차는 B3 구역 117번 [주차]",
     "- (과거 대화) 주차 위치: 사용자가 회사 주차를 B2 구역 52번에 했다고 했어요. [주차]", "117"),
    ("치과 예약 몇 시였지?", "- 치과 예약은 내일 오후 3시 [치과]",
     "- (과거 대화) 치과 예약 변경: 사용자가 치과 예약을 오후 4시로 옮겼다고 했어요. [치과]", "3시"),
]

M = X.M
for v, (head, tail) in VARIANTS.items():
    row = {}
    new = old = 0
    for said, q, key, line, old_key in X.NEWVAL:
        out, _ = X.turn([M("user", said), M("model", X.MEMO_REJECTED)], q, X.SYS, X.REM, search_body=head + line + tail)
        # 방금 값을 먼저 말했는가(옛 값이 없거나 뒤에).
        new += key in out and (old_key not in out or out.index(key) < out.index(old_key))
        old += old_key in out and key not in out
        print(f"[C {v}] {'새 값 ' if key in out else ''}{'옛 값' if old_key in out else ''} | {q} → {out.replace(chr(10), ' / ')[:80]}", flush=True)
    row["C 방금 값 먼저"] = f"{new}/{len(X.NEWVAL)}"
    row["C 옛 값만"] = f"{old}/{len(X.NEWVAL)}"
    ok = 0
    for u0, a0, q, key in WEAK:
        out, _ = X.turn([M("user", u0), M("model", a0)], q, X.SYS, X.REM, search_body=head + UNRELATED + tail)
        ok += key in out
    row["weak"] = f"{ok}/{len(WEAK)}"
    ok = 0
    for q, line, key in HIT:
        out, _ = X.turn(None, q, X.SYS, X.REM, search_body=head + line + tail)
        ok += key in out
        if key not in out:
            print(f"[hit {v}] 틀림 | {q} → {out[:80]}", flush=True)
    row["hit"] = f"{ok}/{len(HIT)}"
    ok = 0
    for u0, a0, q, line, key in EPISODE:
        out, _ = X.turn([M("user", u0), M("model", a0)], q, X.SYS, X.REM, search_body=head + line + tail)
        ok += key in out
        if key not in out:
            print(f"[episode {v}] 틀림 | {q} → {out[:80]}", flush=True)
    row["episode"] = f"{ok}/{len(EPISODE)}"
    ok = 0
    for q, memo, ep, key in CONFLICT:
        out, _ = X.turn(None, q, X.SYS, X.REM, search_body=f"{head}{X.MIXED}\n{memo}\n{ep}{tail}")
        ok += key in out
        if key not in out:
            print(f"[conflict {v}] 틀림 | {q} → {out[:80]}", flush=True)
    row["conflict"] = f"{ok}/{len(CONFLICT)}"
    print(f"== {v}: " + " | ".join(f"{k} {x}" for k, x in row.items()), flush=True)

e = X.engine()
print(f"## 토큰 T1 끝 안내 +{len(e.tokenize(T1_TAIL)) - len(e.tokenize(T0_TAIL))}, T2 머리 +{len(e.tokenize(T2_HEAD))}", flush=True)
