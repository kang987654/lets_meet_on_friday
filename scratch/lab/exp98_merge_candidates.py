"""exp98 — 0.42.3 M3: 메모 합치기 후보를 뜻(EmbeddingGemma 2)으로도 잡을 수 있나.

지금 후보(MemoryMergeGuard.isCandidate): 숫자 집합이 같고 + 숫자 뺀 본문 글자 2-gram 포함률 ≥ 0.3.
더할 후보: 숫자 집합이 같고 + 코사인 ≥ t (앱 메모 벡터 입력과 같은 "title: 태그 | text: 본문").
합성 메모(실제 데이터 아님): 바꿔 말한 중복 10쌍(같은 사실) · 비슷하지만 다른 사실 10쌍 · 잡음 20개.
판정: 중복 쌍 회수 · 다른 사실 쌍이 후보로 끼는 수 · 그 밖의 쌍(잡음 포함) 오후보 수 — 오후보는 최종 oneShot 판정이 걸러 내지만 판정 수(시간)가 는다.
사용: python exp98_merge_candidates.py > exp98_out.txt
"""
import itertools
import pathlib
import re

import litert_lm.embedding_engine as EE

MODEL = pathlib.Path(__file__).resolve().parent.parent / "embeddinggemma-2-740m.litertlm"

DUP = [  # (메모 A, 태그 A, 메모 B, 태그 B) — 같은 사실을 다르게 적음
    ("아침에 커피 대신 녹차를 마심", "음료 차", "아침엔 녹차를 마셔", "음료 습관"),
    ("땅콩 알레르기 있음", "알레르기 건강", "땅콩을 먹으면 알레르기 반응이 남", "건강 음식"),
    ("매주 수요일 저녁에 요가를 함", "운동 요가", "수요일 밤마다 요가 수업에 감", "운동 일정"),
    ("공유기는 거실 TV 옆에 있음", "위치 네트워크", "와이파이 공유기 위치는 거실 텔레비전 옆", "장소 인터넷"),
    ("고양이 이름은 나비", "반려동물 고양이", "우리 집 냥이 이름이 나비야", "반려동물 이름"),
    ("회사는 판교에 있음", "직장 위치", "직장이 판교임", "회사 장소"),
    ("오이를 싫어함", "음식 취향", "오이는 못 먹음", "음식 편식"),
    ("출근은 지하철 신분당선으로 함", "교통 출근", "신분당선 타고 회사 감", "통근 교통"),
    ("어머니 생신은 3월 14일", "가족 생일", "엄마 생일이 3월 14일이야", "가족 기념일"),
    ("자전거 자물쇠 번호는 4821", "비밀번호 자전거", "자물쇠 비번 4821", "번호 자전거"),
]
NEAR = [  # 비슷하지만 다른 사실 — 합치면 안 됨
    ("아침에 녹차를 마심", "음료 차", "저녁에 녹차를 마심", "음료 차"),
    ("땅콩 알레르기 있음", "알레르기 건강", "새우 알레르기 있음", "알레르기 건강"),
    ("매주 수요일 저녁에 요가를 함", "운동 요가", "매주 토요일 아침에 등산을 함", "운동 취미"),
    ("공유기는 거실 TV 옆에 있음", "위치 네트워크", "프린터는 서재 책상 옆에 있음", "위치 기기"),
    ("고양이 이름은 나비", "반려동물 고양이", "강아지 이름은 초코", "반려동물 강아지"),
    ("회사는 판교에 있음", "직장 위치", "집은 분당에 있음", "주거 위치"),
    ("오이를 싫어함", "음식 취향", "당근을 좋아함", "음식 취향"),
    ("출근은 지하철 신분당선으로 함", "교통 출근", "퇴근은 버스로 함", "교통 퇴근"),
    ("어머니 생신은 3월 14일", "가족 생일", "아버지 생신은 3월 14일", "가족 생일"),
    ("집 와이파이 비밀번호는 home2580", "비밀번호 네트워크", "회사 와이파이 비밀번호는 home2580", "비밀번호 회사"),
]
NOISE = [
    ("김치찌개는 익은 김치로 끓임", "요리 레시피"), ("차는 흰색 아반떼", "자동차"), ("혈액형은 A형", "건강"),
    ("좋아하는 가수는 아이유", "음악 취향"), ("안경 도수는 -3.5", "건강 시력"), ("주말엔 늦잠을 잠", "습관 수면"),
    ("사물함 번호는 214번", "번호 회사"), ("회사 주차는 B2-17", "주차 위치"), ("코틀린 공부 중", "학습 프로그래밍"),
    ("헬스장은 집 앞 스포애니", "운동 장소"), ("매운 음식을 잘 먹음", "음식 취향"), ("여권 만료일은 2028년 5월", "여행 서류"),
    ("우산은 현관 신발장에 둠", "물건 위치"), ("동생 이름은 지민", "가족 이름"), ("치과는 강남역 미소치과", "병원 장소"),
    ("겨울엔 붕어빵을 좋아함", "간식 취향"), ("전화 요금제는 5G 무제한", "통신"), ("노트북은 맥북 에어", "기기"),
    ("커피는 오후에만 한 잔", "음료 습관"), ("세탁기는 베란다에 있음", "가전 위치"),
]

DIGITS = re.compile(r"\d+")


def numbers(s):
    return set(DIGITS.findall(s))


def bigrams(s):
    s = re.sub(r"\s+", "", s)
    return {s[i:i + 2] for i in range(len(s) - 1)}


def containment(a, b):
    """BigramMatcher.containment 흉내 — 짧은 쪽 2-gram 이 긴 쪽에 든 비율."""
    ga, gb = bigrams(a), bigrams(b)
    if not ga or not gb:
        return 0.0
    small, big = (ga, gb) if len(ga) <= len(gb) else (gb, ga)
    return len(small & big) / len(small)


def is_candidate_now(a, b):
    return numbers(a) == numbers(b) and containment(DIGITS.sub("", a), DIGITS.sub("", b)) >= 0.3


def main():
    notes = []  # (본문, 태그, 묶음 id)
    for i, (a, ta, b, tb) in enumerate(DUP):
        notes += [(a, ta, f"D{i}"), (b, tb, f"D{i}")]
    for i, (a, ta, b, tb) in enumerate(NEAR):
        notes += [(a, ta, f"N{i}a"), (b, tb, f"N{i}b")]
    notes += [(c, t, f"X{i}") for i, (c, t) in enumerate(NOISE)]

    ee = EE.EmbeddingEngine(str(MODEL), max_input_length=512)
    opts = EE.EmbeddingOptions(normalize=True, input_overflow_strategy=EE.InputOverflowStrategy.TRUNCATE)
    vec = [list(ee.compute_embedding(f"title: {t} | text: {c}", opts).embedding) for c, t, _ in notes]

    def cos(i, j):
        return sum(x * y for x, y in zip(vec[i], vec[j]))

    near_pairs = {(2 * len(DUP) + 2 * k, 2 * len(DUP) + 2 * k + 1) for k in range(len(NEAR))}
    rows = []
    for i, j in itertools.combinations(range(len(notes)), 2):
        dup = notes[i][2] == notes[j][2]
        near = (i, j) in near_pairs
        rows.append((i, j, dup, near, cos(i, j), is_candidate_now(notes[i][0], notes[j][0]), numbers(notes[i][0]) == numbers(notes[j][0])))

    print("== 중복 쌍 코사인 / 지금 후보")
    for i, j, dup, near, c, now, _ in rows:
        if dup:
            print(f"  {c:.3f} {'후보' if now else '  - '}  {notes[i][0]}  ↔  {notes[j][0]}")
    print("== 비슷하지만 다른 쌍 코사인 / 지금 후보")
    for i, j, dup, near, c, now, _ in rows:
        if near:
            print(f"  {c:.3f} {'후보' if now else '  - '}  {notes[i][0]}  ↔  {notes[j][0]}")
    others = sorted((r for r in rows if not r[2] and not r[3]), key=lambda r: -r[4])[:10]
    print("== 그 밖의 쌍 코사인 상위 10")
    for i, j, dup, near, c, now, _ in others:
        print(f"  {c:.3f} {'후보' if now else '  - '}  {notes[i][0]}  ↔  {notes[j][0]}")

    total_pairs = len(rows)
    print(f"\n== 문턱별 (전체 쌍 {total_pairs}, 중복 {len(DUP)}, 비슷하지만 다름 {len(NEAR)})")
    now_dup = sum(r[5] for r in rows if r[2])
    now_near = sum(r[5] for r in rows if r[3])
    now_other = sum(r[5] for r in rows if not r[2] and not r[3])
    print(f"지금 규칙: 중복 {now_dup}/{len(DUP)} · 다름 끼임 {now_near} · 그 밖 오후보 {now_other}")
    for t in (0.70, 0.75, 0.80, 0.85, 0.88, 0.90, 0.92):
        add = [r for r in rows if r[6] and r[4] >= t]  # 숫자 같음 + 코사인
        union = lambda pred: sum((r[5] or (r[6] and r[4] >= t)) and pred(r) for r in rows)
        print(f"지금 ∪ (숫자 같음 ∧ 코사인 ≥ {t:.2f}): 중복 {union(lambda r: r[2])}/{len(DUP)} · 다름 끼임 {union(lambda r: r[3])} · "
              f"그 밖 오후보 {union(lambda r: not r[2] and not r[3])} (뜻으로만 더해진 쌍 {len(add)})")


if __name__ == "__main__":
    main()
