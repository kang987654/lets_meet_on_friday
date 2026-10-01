"""exp42b — 병합 오판(exp42: "자전거 자물쇠 번호 4821" + "현관 비밀번호 4821" → 같음) 막기.

## 후보
- 결정적 가드(모델 앞): ① 두 기억의 **숫자 집합이 같아야** 후보(숫자가 다르면 다른 사실 — 4821/4812)
  ② 숫자를 뺀 본문의 바이그램 containment ≥ 임계(대상이 겹쳐야 — "현관" vs "자전거 자물쇠")
- 프롬프트 v2: 판정 전에 두 기억의 **대상**을 한 줄씩 적게 해 비교를 강제

## 게이트
오병합 0 (함정 9개 — exp42 의 5개 + 대상만 다른 4개), 참 중복 재현 ≥ 7/8, 숫자 손실 0.
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
    "현관 비밀번호는 4821", "자전거 자물쇠 비밀번호는 4812", "남동생 생일은 5월 3일",
    "회사 퇴근 시간은 오후 6시", "땅콩버터를 좋아한다",
    # 대상만 다른 함정(숫자·형식 동일)
    "회사 와이파이 비밀번호는 kosmos123", "엄마 생일은 5월 3일", "사물함 번호 4821", "학원 출근 시간은 오전 9시",
]
OTHERS = [
    "주말마다 한강에서 러닝을 한다", "강아지 이름은 콩이", "차량 번호는 12가 3456",
    "좋아하는 영화는 인터스텔라", "치과는 강남역 근처 미소치과", "매주 수요일 저녁 기타 레슨",
    "혈액형은 A형", "회사는 판교에 있다", "고양이 털 알레르기는 없다", "운동화 사이즈 270",
]
NOTES = [(g, t) for g, ts in GROUPS.items() for t in ts] + [("T", t) for t in TRAPS] + [("O", t) for t in OTHERS]
DIGITS = re.compile(r"\d+")


def bigrams(s):
    c = re.sub(r"[\s\W_]+", "", s)
    return {c[i:i + 2] for i in range(len(c) - 1)}


def containment(a, b):
    x, y = bigrams(a), bigrams(b)
    return len(x & y) / min(len(x), len(y)) if x and y else 0.0


def guard(a, b, th):
    if set(DIGITS.findall(a)) != set(DIGITS.findall(b)):
        return False
    return containment(DIGITS.sub("", a), DIGITS.sub("", b)) >= th


def true_pair(i, j):
    return NOTES[i][0] == NOTES[j][0] and NOTES[i][0].startswith("G")


pairs = list(itertools.combinations(range(len(NOTES)), 2))
trues = [p for p in pairs if true_pair(*p)]
print("== 가드 임계 ==")
for th in (0.2, 0.25, 0.3, 0.4):
    c = [p for p in pairs if guard(NOTES[p[0]][1], NOTES[p[1]][1], th)]
    print(f"th={th} 후보 {len(c)} 참중복 {sum(true_pair(*p) for p in c)}/{len(trues)} 함정쌍 "
          f"{sum(1 for p in c if 'T' in (NOTES[p[0]][0], NOTES[p[1]][0]))}")
TH = 0.25
cands = [p for p in pairs if guard(NOTES[p[0]][1], NOTES[p[1]][1], TH)]

V1 = """너는 기억 정리 도우미다. 아래 기억들이 완전히 같은 사실을 말하는지 판정한다.
숫자·날짜·시각·사람·대상이 하나라도 다르면 '다름'이다. 비슷한 주제일 뿐이어도 '다름'이다.
같으면 숫자와 고유명사를 그대로 살려 가장 정확한 한 문장으로 합친다.
출력은 정확히 두 줄:
판정: 같음 또는 다름
합친 문장: 같을 때 한 문장, 다르면 없음"""
V2 = """너는 기억 정리 도우미다. 아래 기억들이 완전히 같은 사실을 말하는지 판정한다.
먼저 각 기억이 무엇에 관한 것인지(사람·물건·장소) 대상을 적는다. 대상이 하나라도 다르면 '다름'이다.
숫자·날짜·시각이 다르면 '다름'이다. 비슷한 주제일 뿐이어도 '다름'이다.
같으면 숫자와 고유명사를 그대로 살려 가장 정확한 한 문장으로 합친다.
출력 형식:
대상: 기억마다 대상을 쉼표로
판정: 같음 또는 다름
합친 문장: 같을 때 한 문장, 다르면 없음"""

engine = K.create_engine()


def judge(system, items):
    body = "\n".join(f"- {t}" for t in items)
    with K.create_chat_conversation(engine, tools=[], system_message=system) as conv:
        res = conv.send_message(f"기억들:\n{body}")
    content = res.get("content")
    text = "".join(c.get("text", "") for c in content) if isinstance(content, list) else str(content)
    m = re.search(r"판정\s*[:：]\s*(같음|다름)", text)
    merged = re.search(r"합친 문장\s*[:：]\s*(.+)", text)
    return (m.group(1) if m else None), (merged.group(1).strip() if merged else "")


for name, system in (("v1", V1), ("v2", V2)):
    tp = fp = miss = fmt = lost = 0
    for i, j in cands:
        a, b = NOTES[i][1], NOTES[j][1]
        verdict, merged = judge(system, [a, b])
        t = true_pair(i, j)
        fmt += verdict is None
        if verdict == "같음" and t:
            tp += 1
            lost += not set(DIGITS.findall(a + b)) <= set(DIGITS.findall(merged))
        elif verdict == "같음":
            fp += 1
            print(f"  {name} 오병합: {a} | {b} → {merged}", flush=True)
        elif t:
            miss += 1
            print(f"  {name} 놓침: {a} | {b}", flush=True)
    print(f"== {name} (가드 th={TH}, 후보 {len(cands)}): 참중복 {tp}/{sum(true_pair(*p) for p in cands)} "
          f"오병합 {fp} 놓침 {miss} 형식이탈 {fmt} 숫자손실 {lost}", flush=True)
