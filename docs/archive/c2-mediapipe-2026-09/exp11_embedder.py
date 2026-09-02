"""exp11 — RAG 임베더의 한국어 의미 분별력 측정.

앱은 `app/src/main/assets/models/universal_sentence_encoder.tflite` 를 MediaPipe TextEmbedder 로
올려 메모를 임베딩하고, `KnowledgeRepositoryImpl.searchByVector` 가 코사인 유사도로 상위 3건을
뽑는다(임계값 없음). 그런데 이 모델의 어휘를 뜯어보니 한글이 **1글자 토큰 16,079개 / 2글자
이상 8개** 로, 학습된 서브워드가 아니라 유니코드 음절 블록 전체를 넣은 fallback 모양이었다.
그렇다면 한국어 유사도가 의미가 아니라 **글자 겹침**에 좌우될 수 있다.

이 실험이 재는 것:
  1) 대조군(영어) — 모델이 애초에 의미를 잡는가
  2) 한국어 관련쌍 vs 무관쌍 — 분리가 되는가
  3) **글자 겹침 함정** — 의미는 무관하지만 글자가 겹치는 쌍 vs 의미는 같지만 글자가 다른 쌍.
     char-level 이면 전자가 후자보다 높게 나온다. 이게 결정적 판별이다.
  4) 실제 검색 시뮬레이션 — 앱과 같은 방식(top-1)으로 정답 메모를 집어내는가

앱과 맞춘 조건: 같은 .tflite, TextEmbedderOptions 기본값(l2_normalize/quantize 미설정),
코사인은 앱의 `cosineSimilarity` 와 같은 정의(dot / (|a||b|)).
"""

import os
import sys

import numpy as np
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import text as mp_text

sys.stdout.reconfigure(encoding="utf-8")

MODEL = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main",
                 "assets", "models", "universal_sentence_encoder.tflite")
)


def make_embedder():
    base = mp_python.BaseOptions(model_asset_path=MODEL)
    # [WHY] 앱은 TextEmbedderOptions 를 기본값으로 만든다(quantize/l2_normalize 미설정).
    return mp_text.TextEmbedder.create_from_options(mp_text.TextEmbedderOptions(base_options=base))


def vec(embedder, s):
    return np.array(embedder.embed(s).embeddings[0].embedding, dtype=np.float64)


def cos(a, b):
    # 앱 `KnowledgeRepositoryImpl.cosineSimilarity` 와 같은 정의
    denom = np.linalg.norm(a) * np.linalg.norm(b)
    return float(np.dot(a, b) / denom) if denom else 0.0


# ---------------------------------------------------------------- 1) 대조군(영어)
EN_RELATED = [
    ("My bicycle lock code is 1234", "What was my bike password again?"),
    ("I prefer green tea over coffee", "Which drink do I like?"),
    ("Dentist appointment tomorrow at 3pm", "When is my dental visit?"),
]
EN_UNRELATED = [
    ("My bicycle lock code is 1234", "Tell me about the singer Nayeon"),
    ("I prefer green tea over coffee", "What is photosynthesis?"),
    ("Dentist appointment tomorrow at 3pm", "How tall is Mount Everest?"),
]

# ---------------------------------------------------------------- 2) 한국어
KO_RELATED = [
    ("자전거 비밀번호는 1234", "내 자전거 비밀번호 뭐였지?"),
    ("커피보다 녹차를 더 좋아함", "내가 뭘 좋아한다고 했지?"),
    ("내일 3시 치과 예약", "치과는 언제 가기로 했지?"),
    ("우리집 와이파이 비번은 aB3x9k", "집 인터넷 비밀번호 알려줘"),
]
KO_UNRELATED = [
    ("자전거 비밀번호는 1234", "트와이스 나연에 대해 알려줘"),
    ("커피보다 녹차를 더 좋아함", "광합성이 뭐야?"),
    ("내일 3시 치과 예약", "에베레스트 산 높이가 얼마야?"),
    ("우리집 와이파이 비번은 aB3x9k", "오늘 날씨 어때?"),
]

# ---------------------------------------------------------------- 3) 글자 겹침 함정
#   (기준 문장, 의미는 무관하나 글자가 겹치는 문장, 의미는 같으나 글자가 다른 문장)
TRAPS = [
    (
        "커피보다 녹차를 더 좋아함",
        "녹차 아이스크림 칼로리가 얼마야",          # 글자 겹침(녹차), 의미 무관
        "제가 선호하는 음료가 무엇인가요",            # 글자 안 겹침, 의미 관련
    ),
    (
        "자전거 비밀번호는 1234",
        "자전거 도로가 어디에 있어",                  # 글자 겹침(자전거), 의미 무관
        "잠금장치 번호가 뭐였더라",                   # 글자 안 겹침, 의미 관련
    ),
    (
        "내일 3시 치과 예약",
        "치과 의사 연봉이 궁금해",                    # 글자 겹침(치과), 의미 무관
        "병원 가기로 한 시간이 언제였지",             # 글자 안 겹침, 의미 관련
    ),
]

# ---------------------------------------------------------------- 4) 검색 시뮬레이션
MEMORY_BANK = [
    "자전거 비밀번호는 1234",
    "우리집 와이파이 비번은 aB3x9k",
    "커피보다 녹차를 더 좋아함",
    "여동생 생일은 3월 12일",
    "회사 주차장은 지하 2층 B구역",
    "알레르기: 땅콩",
    "넷플릭스 계정은 가족 공유 중",
    "매주 화요일 저녁에 헬스장에 간다",
]
QUERIES = [
    ("내 자전거 비밀번호 뭐였지?", "자전거 비밀번호는 1234"),
    ("집 와이파이 비번 알려줘", "우리집 와이파이 비번은 aB3x9k"),
    ("내가 뭘 좋아한다고 했지?", "커피보다 녹차를 더 좋아함"),
    ("동생 생일 언제야?", "여동생 생일은 3월 12일"),
    ("주차 어디에 하지?", "회사 주차장은 지하 2층 B구역"),
    ("나 못 먹는 음식 있었나?", "알레르기: 땅콩"),
    ("운동 무슨 요일에 가지?", "매주 화요일 저녁에 헬스장에 간다"),
]


def report_pairs(embedder, title, pairs):
    print(f"\n{title}")
    scores = []
    for a, b in pairs:
        s = cos(vec(embedder, a), vec(embedder, b))
        scores.append(s)
        print(f"    {s:6.3f}   {a}  ↔  {b}")
    print(f"    ── 평균 {np.mean(scores):.3f}  (최소 {min(scores):.3f} / 최대 {max(scores):.3f})")
    return scores


def main():
    print(f"모델: {MODEL}")
    embedder = make_embedder()
    dim = len(vec(embedder, "test"))
    print(f"임베딩 차원: {dim}")

    print("\n" + "=" * 90)
    print("1) 대조군(영어) — 모델이 애초에 의미를 잡는가")
    print("=" * 90)
    en_rel = report_pairs(embedder, "  [관련쌍]", EN_RELATED)
    en_unrel = report_pairs(embedder, "  [무관쌍]", EN_UNRELATED)
    en_gap = np.mean(en_rel) - np.mean(en_unrel)
    print(f"\n  ▶ 영어 분리도(관련 평균 − 무관 평균): {en_gap:+.3f}")

    print("\n" + "=" * 90)
    print("2) 한국어 — 관련쌍 vs 무관쌍")
    print("=" * 90)
    ko_rel = report_pairs(embedder, "  [관련쌍]", KO_RELATED)
    ko_unrel = report_pairs(embedder, "  [무관쌍]", KO_UNRELATED)
    ko_gap = np.mean(ko_rel) - np.mean(ko_unrel)
    print(f"\n  ▶ 한국어 분리도: {ko_gap:+.3f}   (영어 {en_gap:+.3f})")
    overlap = max(ko_unrel) >= min(ko_rel)
    print(f"  ▶ 관련/무관 점수 구간이 겹치는가: {'예 — 임계값으로 가를 수 없다' if overlap else '아니오'}")

    print("\n" + "=" * 90)
    print("3) 글자 겹침 함정 — char-level 이면 '글자 겹침·의미 무관' 이 더 높게 나온다")
    print("=" * 90)
    wrong = 0
    for base, lexical, semantic in TRAPS:
        v = vec(embedder, base)
        s_lex = cos(v, vec(embedder, lexical))
        s_sem = cos(v, vec(embedder, semantic))
        bad = s_lex > s_sem
        wrong += bad
        print(f"\n  기준: {base}")
        print(f"    {s_lex:6.3f}  글자겹침·의미무관 : {lexical}")
        print(f"    {s_sem:6.3f}  글자다름·의미관련 : {semantic}")
        print(f"    → {'글자 겹침이 이겼다 (char-level 징후)' if bad else '의미가 이겼다'}")
    print(f"\n  ▶ 글자 겹침이 이긴 횟수: {wrong}/{len(TRAPS)}")

    print("\n" + "=" * 90)
    print("4) 검색 시뮬레이션 — 앱과 같은 방식(전량 코사인 → 상위 3)")
    print("=" * 90)
    bank = [(m, vec(embedder, m)) for m in MEMORY_BANK]
    top1 = 0
    top3 = 0
    all_top_scores = []
    for q, want in QUERIES:
        qv = vec(embedder, q)
        ranked = sorted(((cos(qv, mv), m) for m, mv in bank), reverse=True)
        names = [m for _, m in ranked[:3]]
        hit1 = names[0] == want
        hit3 = want in names
        top1 += hit1
        top3 += hit3
        all_top_scores.append(ranked[0][0])
        print(f"\n  질의: {q}")
        print(f"    정답: {want}")
        for s, m in ranked[:3]:
            mark = "★" if m == want else " "
            print(f"    {mark} {s:6.3f}  {m}")
        print(f"    → top1 {'O' if hit1 else 'X'} / top3 {'O' if hit3 else 'X'}")
    print(f"\n  ▶ top-1 정확도 {top1}/{len(QUERIES)}   top-3 정확도 {top3}/{len(QUERIES)}")
    print(f"  ▶ 1위 점수 범위 {min(all_top_scores):.3f} ~ {max(all_top_scores):.3f}"
          f" (임계값 후보 판단용)")

    embedder.close()


if __name__ == "__main__":
    main()
