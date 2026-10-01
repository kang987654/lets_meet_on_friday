"""exp42 — 수동 기억 정리(0.30.0) M0: 중복 후보 임계, 병합 판정 프롬프트, 주간 회고 프롬프트.

## 왜 필요한가
병합은 기억을 **지운다**. 모델이 "현관 비밀번호 4821"과 "자전거 자물쇠 4821"을 같은 사실로 합치면 되돌릴 수 없다.
그래서 (a) 바이그램 후보 단계는 재현율을 보고 (b) 모델 판정 단계는 **오병합 0** 을 게이트로 본다.
회고는 에피소드 요약에 없는 일을 지어내지 않는지 본다.

## 데이터 — 전부 합성(실기기 DB 파생 금지)
중복 6묶음(같은 사실, 표현만 다름) + 함정 5개(비슷한 표현, 다른 사실: 대상·숫자·사람이 다름) + 무관 10개.

## 게이트
(a) 임계에서 참 중복 쌍 재현율 100%  (b) 오병합 0, 참 중복 판정 ≥ 90%, 합친 문장의 숫자 보존 100%
(c) 회고 3~5문장·한국어·요약에 없는 고유명사/숫자 0
"""
import itertools
import re
import sys

import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

GROUPS = {
    "G1": ["자전거 자물쇠 비밀번호는 4821", "자전거 자물쇠 번호 4821", "내 자전거 자물쇠 비번은 4821이야"],
    "G2": ["와이파이 비밀번호는 kosmos123", "집 와이파이 비번 kosmos123"],
    "G3": ["땅콩 알레르기가 있다", "사용자는 땅콩 알레르기 있음"],
    "G4": ["아침에 커피 대신 녹차를 마신다", "커피보다 녹차를 좋아함"],
    "G5": ["회사 출근 시간은 오전 9시", "출근은 오전 9시까지"],
    "G6": ["여동생 생일은 5월 3일", "여동생 생일 5월 3일"],
}
TRAPS = [
    "현관 비밀번호는 4821",            # G1 과 숫자 같고 대상 다름
    "자전거 자물쇠 비밀번호는 4812",   # G1 과 대상 같고 숫자 다름
    "남동생 생일은 5월 3일",           # G6 과 날짜 같고 사람 다름
    "회사 퇴근 시간은 오후 6시",       # G5 와 비슷, 다른 사실
    "땅콩버터를 좋아한다",             # G3 과 단어 겹침, 반대에 가까운 사실
]
OTHERS = [
    "주말마다 한강에서 러닝을 한다", "강아지 이름은 콩이", "차량 번호는 12가 3456",
    "좋아하는 영화는 인터스텔라", "치과는 강남역 근처 미소치과", "매주 수요일 저녁 기타 레슨",
    "혈액형은 A형", "회사는 판교에 있다", "고양이 털 알레르기는 없다", "운동화 사이즈 270",
]

NOTES = [(g, t) for g, ts in GROUPS.items() for t in ts] + [("T", t) for t in TRAPS] + [("O", t) for t in OTHERS]


def bigrams(s: str) -> set:
    c = re.sub(r"[\s\W_]+", "", s)
    return {c[i:i + 2] for i in range(len(c) - 1)}


def containment(a: str, b: str) -> float:
    x, y = bigrams(a), bigrams(b)
    return len(x & y) / min(len(x), len(y)) if x and y else 0.0


def jaccard(a: str, b: str) -> float:
    x, y = bigrams(a), bigrams(b)
    return len(x & y) / len(x | y) if x | y else 0.0


def is_true_pair(i, j):
    gi, gj = NOTES[i][0], NOTES[j][0]
    return gi == gj and gi.startswith("G")


pairs = list(itertools.combinations(range(len(NOTES)), 2))
true_pairs = [p for p in pairs if is_true_pair(*p)]

print("== (a) 후보 임계 ==")
for name, fn in (("containment", containment), ("jaccard", jaccard)):
    for th in (0.2, 0.3, 0.4, 0.5, 0.6):
        cand = [p for p in pairs if fn(NOTES[p[0]][1], NOTES[p[1]][1]) >= th]
        hit = sum(1 for p in cand if is_true_pair(*p))
        print(f"{name:11} th={th:.1f} 후보 {len(cand):3} 참중복 {hit}/{len(true_pairs)}", flush=True)
for p in true_pairs:
    a, b = NOTES[p[0]][1], NOTES[p[1]][1]
    print(f"  참쌍 c={containment(a, b):.2f} j={jaccard(a, b):.2f}  {a} | {b}")

TH = float(sys.argv[1]) if len(sys.argv) > 1 else 0.3
candidates = [p for p in pairs if containment(NOTES[p[0]][1], NOTES[p[1]][1]) >= TH]
print(f"\n채택 후보: containment >= {TH} → {len(candidates)}쌍")

JUDGE_SYSTEM = """너는 기억 정리 도우미다. 아래 기억들이 완전히 같은 사실을 말하는지 판정한다.
숫자·날짜·시각·사람·대상이 하나라도 다르면 '다름'이다. 비슷한 주제일 뿐이어도 '다름'이다.
같으면 숫자와 고유명사를 그대로 살려 가장 정확한 한 문장으로 합친다.
출력은 정확히 두 줄:
판정: 같음 또는 다름
합친 문장: 같을 때 한 문장, 다르면 없음"""


def judge(items):
    body = "\n".join(f"- {t}" for t in items)
    with K.create_chat_conversation(engine, tools=[], system_message=JUDGE_SYSTEM) as conv:
        res = conv.send_message(f"기억들:\n{body}")
    content = res.get("content")
    text = "".join(c.get("text", "") for c in content) if isinstance(content, list) else str(res.get("text", content))
    m = re.search(r"판정\s*[:：]\s*(같음|다름)", text)
    merged = re.search(r"합친 문장\s*[:：]\s*(.+)", text)
    return (m.group(1) if m else None), (merged.group(1).strip() if merged else ""), text


engine = K.create_engine()
print("\n== (b) 병합 판정 ==")
tp = fp = fn_ = bad_fmt = num_lost = 0
for i, j in candidates:
    a, b = NOTES[i][1], NOTES[j][1]
    verdict, merged, raw = judge([a, b])
    truth = is_true_pair(i, j)
    if verdict is None:
        bad_fmt += 1
    if verdict == "같음" and truth:
        tp += 1
        digits = set(re.findall(r"\d+", a + b))
        if not digits <= set(re.findall(r"\d+", merged)):
            num_lost += 1
    elif verdict == "같음" and not truth:
        fp += 1
    elif truth:
        fn_ += 1
    flag = "오병합!" if verdict == "같음" and not truth else ("놓침" if truth and verdict != "같음" else "")
    print(f"{'참' if truth else '거짓'} {verdict} {flag:4} {a} | {b} → {merged}", flush=True)
cand_true = sum(1 for p in candidates if is_true_pair(*p))
print(f"== 판정: 참중복 {tp}/{cand_true}  오병합 {fp}  놓침 {fn_}  형식이탈 {bad_fmt}  숫자손실 {num_lost}")

# 3개짜리 묶음(G1) — 쌍 판정 후 묶음 전체를 한 번 더 합치는 경로
verdict, merged, _ = judge(GROUPS["G1"])
print(f"G1 3개 묶음 → {verdict} / {merged}")

print("\n== (c) 주간 회고 ==")
EPISODES = [
    ("9월 24일", "치과 예약 일정 추가", "내일 오후 3시 치과 예약을 일정에 넣었다."),
    ("9월 25일", "자전거 자물쇠 번호 저장", "자전거 자물쇠 비밀번호 4821을 기억해 달라고 했다."),
    ("9월 26일", "주말 러닝 계획", "토요일 아침 한강에서 5km 러닝을 하기로 했다."),
    ("9월 28일", "프로젝트 마감 걱정", "금요일 마감인 발표 자료 준비가 늦어 걱정했다. 목차를 같이 정리했다."),
    ("9월 29일", "기타 레슨 시간 변경", "수요일 기타 레슨이 저녁 8시로 바뀌었다."),
    ("9월 30일", "물 마시기 알림", "오전 7시 4분에 물 마시기 알림을 설정했다."),
]
RETRO_SYSTEM = """너는 사용자의 개인 비서다. 아래는 지난 7일 동안 나눈 대화의 요약이다.
이번 주를 3~5문장의 한국어로 회고한다. 무엇에 시간을 썼는지와 다가오는 일을 짚는다.
요약에 없는 사실·숫자·이름은 절대 지어내지 않는다. 목록이나 제목 없이 문단 하나로 쓴다."""
body = "\n".join(f"- {d} · {t}: {s}" for d, t, s in EPISODES)
source_tokens = set(re.findall(r"\d+|[가-힣A-Za-z]{2,}", body))
for run in range(3):
    with K.create_chat_conversation(engine, tools=[], system_message=RETRO_SYSTEM) as conv:
        res = conv.send_message(f"지난 7일 대화 요약:\n{body}")
    content = res.get("content")
    text = ("".join(c.get("text", "") for c in content) if isinstance(content, list) else str(content)).strip()
    sentences = [s for s in re.split(r"(?<=[.!?])\s+", text) if s.strip()]
    new_numbers = [n for n in re.findall(r"\d+", text) if n not in source_tokens]
    print(f"[회고 {run}] 문장 {len(sentences)}  새 숫자 {new_numbers}\n{text}\n", flush=True)
