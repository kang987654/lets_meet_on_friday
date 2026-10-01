"""exp44 — 기억 정리 2차(0.31.0) M0: 에피소드 통합 후보 규칙, 판정 프롬프트, 통합 재요약.

## 왜 필요한가
exp42 는 한 줄 사실 노트만 쟀다. 에피소드는 제목·태그·여러 문장 요약이라 ① 후보 규칙 ② "같은 일이 이어진 것인가" 판정
③ 합친 대화를 기존 요약 프롬프트(SummarizeEpisodeUseCase, exp33)가 한 문서로 요약하는지 — 셋 다 미측정이다.

## 데이터 — 전부 합성
시간순 에피소드 20개. 같은 일이 끊긴 인접 쌍 6(참), 시간만 인접한 다른 일 5(함정), 같은 주제지만 며칠 떨어진 쌍 3(시간 규칙이 걸러야 함).

## 게이트 (계획서 0.31.0)
(a) 인접(≤24h) 후보에서 참 쌍 재현 6/6  (b) 오병합(다른 일 합침) ≤ 1 — 2건 이상이면 사용자에게 범위 재확인
(c) 참 쌍 재요약이 **문서 1편**(분리되지 않음)이고 양쪽 핵심어를 모두 담음
"""
import re
import sys

import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

H = 3600
D = 24 * H
# (id, 시작 시각(초, 9/24 00:00 기준), 끝, 제목, 태그, 요약, 짝 라벨) — 짝 라벨이 같은 인접 쌍이 참.
EPS = [
    ("e01", 0 * D + 10 * H, 0 * D + 10.5 * H, "치과 예약 일정 추가", "치과, 예약, 일정, 강남, 미소치과",
     "사용자가 9월 25일 오후 3시 강남 미소치과 예약을 일정에 추가해 달라고 했다.", "P1"),
    ("e02", 0 * D + 11.2 * H, 0 * D + 11.5 * H, "치과 예약 시간 변경", "치과, 예약, 변경, 시간, 미소치과",
     "미소치과 예약을 오후 3시에서 오후 4시로 옮겨 달라고 했다.", "P1"),
    ("e03", 0 * D + 20 * H, 0 * D + 20.5 * H, "저녁 운동 계획", "운동, 헬스, 저녁, 루틴",
     "저녁 8시에 헬스장에서 하체 운동을 하기로 계획했다.", "T1"),
    ("e04", 1 * D + 9 * H, 1 * D + 9.5 * H, "발표 자료 목차 정리", "발표, 자료, 목차, 마감, 프로젝트",
     "금요일 마감인 프로젝트 발표 자료의 목차를 같이 정리했다.", "P2"),
    ("e05", 1 * D + 10.5 * H, 1 * D + 11 * H, "발표 자료 슬라이드 구성", "발표, 슬라이드, 자료, 구성, 프로젝트",
     "정리한 목차를 바탕으로 발표 슬라이드 10장 구성을 잡았다.", "P2"),
    ("e06", 1 * D + 13 * H, 1 * D + 13.3 * H, "점심 메뉴 추천", "점심, 메뉴, 국밥, 추천",
     "점심으로 국밥을 추천받았다.", "T2"),
    ("e07", 2 * D + 8 * H, 2 * D + 8.3 * H, "자전거 자물쇠 번호 저장", "자전거, 자물쇠, 비밀번호, 4821",
     "자전거 자물쇠 비밀번호 4821을 기억해 달라고 했다.", "T3"),
    ("e08", 2 * D + 9 * H, 2 * D + 9.4 * H, "와이파이 비밀번호 저장", "와이파이, 비밀번호, 집, kosmos123",
     "집 와이파이 비밀번호 kosmos123을 기억해 달라고 했다.", "T3"),
    ("e09", 2 * D + 19 * H, 2 * D + 19.5 * H, "주말 러닝 계획", "러닝, 한강, 주말, 5km",
     "토요일 아침 한강에서 5km 러닝을 하기로 했다.", "P3"),
    ("e10", 2 * D + 20.2 * H, 2 * D + 20.5 * H, "러닝 준비물", "러닝, 운동화, 물, 준비물, 한강",
     "토요일 한강 러닝에 챙길 운동화와 물을 정리했다.", "P3"),
    ("e11", 3 * D + 14 * H, 3 * D + 14.4 * H, "엄마 생신 선물 고민", "엄마, 생신, 선물, 꽃, 고민",
     "엄마 생신 선물로 꽃과 스카프 중에 고민했다.", "P4"),
    ("e12", 3 * D + 15 * H, 3 * D + 15.3 * H, "생신 선물 결정과 주문", "엄마, 생신, 선물, 스카프, 주문",
     "엄마 생신 선물로 스카프를 주문하기로 결정했다.", "P4"),
    ("e13", 3 * D + 21 * H, 3 * D + 21.3 * H, "회사 회의 일정 추가", "회의, 일정, 회사, 팀",
     "다음 주 월요일 오전 10시 팀 회의 일정을 추가했다.", "T4"),
    ("e14", 4 * D + 9 * H, 4 * D + 9.3 * H, "치과 진료 후기", "치과, 진료, 스케일링, 후기",
     "어제 미소치과에서 스케일링을 받았다고 이야기했다.", "F1"),
    ("e15", 4 * D + 18 * H, 4 * D + 18.4 * H, "기타 레슨 시간 변경", "기타, 레슨, 수요일, 시간, 변경",
     "수요일 기타 레슨이 저녁 8시로 바뀌었다.", "P5"),
    ("e16", 4 * D + 19 * H, 4 * D + 19.3 * H, "기타 레슨 연습곡", "기타, 레슨, 연습곡, 코드",
     "다음 기타 레슨까지 연습할 곡과 코드를 정했다.", "P5"),
    ("e17", 5 * D + 10 * H, 5 * D + 10.3 * H, "회사 회의 안건 정리", "회의, 안건, 회사, 팀, 월요일",
     "월요일 팀 회의 안건 세 가지를 정리했다.", "F2"),
    ("e18", 5 * D + 12 * H, 5 * D + 12.3 * H, "회의실 예약", "회의실, 예약, 회의, 월요일",
     "월요일 팀 회의에 쓸 회의실을 예약해 달라고 했다.", "P6"),
    ("e19", 5 * D + 12.8 * H, 5 * D + 13.0 * H, "회의 참석자 알림", "회의, 참석자, 알림, 월요일",
     "월요일 팀 회의 참석자에게 보낼 알림 문구를 같이 썼다.", "P6"),
    ("e20", 6 * D + 20 * H, 6 * D + 20.4 * H, "주간 운동 기록", "운동, 기록, 헬스, 러닝",
     "이번 주 헬스와 러닝 기록을 정리했다.", "F3"),
]
# 참 쌍 = 같은 P 라벨의 연속 에피소드. F 라벨은 "며칠 떨어진 같은 주제"(e14↔e01·e02, e17↔e13, e20↔e03·e09)로 인접 아님.


def bigrams(s):
    c = re.sub(r"[\s\W_]+", "", s)
    return {c[i:i + 2] for i in range(len(c) - 1)}


def containment(a, b):
    x, y = bigrams(a), bigrams(b)
    return len(x & y) / min(len(x), len(y)) if x and y else 0.0


pairs = [(EPS[i], EPS[i + 1]) for i in range(len(EPS) - 1) if EPS[i + 1][1] - EPS[i][2] <= D]
truth = {(a[0], b[0]) for a, b in pairs if a[6] == b[6] and a[6].startswith("P")}
print(f"인접(≤24h) 쌍 {len(pairs)}  참 {len(truth)}")
print("== (a) 후보 임계 (제목+태그) ==")
for th in (0.2, 0.3, 0.4, 0.5):
    cand = [(a, b) for a, b in pairs if containment(a[3] + " " + a[4], b[3] + " " + b[4]) >= th]
    hit = sum((a[0], b[0]) in truth for a, b in cand)
    print(f"th={th} 후보 {len(cand)} 참 {hit}/{len(truth)}", flush=True)
for a, b in pairs:
    print(f"  {'참' if (a[0], b[0]) in truth else '  '} c={containment(a[3] + ' ' + a[4], b[3] + ' ' + b[4]):.2f}  {a[3]} | {b[3]}")

TH = float(sys.argv[1]) if len(sys.argv) > 1 else 0.3
cands = [(a, b) for a, b in pairs if containment(a[3] + " " + a[4], b[3] + " " + b[4]) >= TH]

JUDGE = """너는 대화 기록 정리 도우미다. 아래 두 대화 요약이 같은 일이 이어진 것인지 판정한다.
같은 일이란 같은 약속·작업·계획·문제를 계속 이야기한 것이다. 분야가 같아도(둘 다 일정, 둘 다 비밀번호) 다른 일이면 '다름'이다.
출력은 정확히 한 줄: 판정: 같음 또는 다름"""


def fmt(e):
    day = 24 + int(e[1] // D)
    hour = int((e[1] % D) // H)
    return f"9월 {day}일 {hour}시 · 제목: {e[3]} · 태그: {e[4]} · 요약: {e[5]}"


engine = K.create_engine()


def ask(system, user):
    with K.create_chat_conversation(engine, tools=[], system_message=system) as conv:
        res = conv.send_message(user)
    content = res.get("content")
    return "".join(c.get("text", "") for c in content) if isinstance(content, list) else str(content)


print(f"\n== (b) 판정 (후보 th={TH}, {len(cands)}쌍) ==")
tp = fp = miss = fmt_err = 0
for a, b in cands:
    out = ask(JUDGE, f"대화 A: {fmt(a)}\n대화 B: {fmt(b)}")
    m = re.search(r"판정\s*[:：]\s*(같음|다름)", out)
    v = m.group(1) if m else None
    t = (a[0], b[0]) in truth
    fmt_err += v is None
    if v == "같음" and t:
        tp += 1
    elif v == "같음":
        fp += 1
        print(f"  오병합: {a[3]} | {b[3]}", flush=True)
    elif t:
        miss += 1
        print(f"  놓침: {a[3]} | {b[3]}", flush=True)
print(f"== 판정: 참 {tp}/{sum((a[0], b[0]) in truth for a, b in cands)}  오병합 {fp}  놓침 {miss}  형식이탈 {fmt_err}", flush=True)

print("\n== (c) 통합 재요약 (SummarizeEpisodeUseCase 프롬프트 원문) ==")
SUMMARY_SYSTEM = """[System]
You are a librarian who summarizes conversation logs into searchable index documents.
당신은 대화 기록을 색인용 문서로 요약하는 사서입니다. 아래 형식만 출력하세요.
제목: (한 줄)
태그: (쉼표로 구분한 핵심 명사 5~8개 — 검색에 쓰이므로 동의어·구체 명사 포함)
요약: (3~5문장. 날짜·시각·이름·숫자는 원문 그대로 보존)

대화에 서로 무관한 주제가 2개 이상 섞여 있으면, 주제마다 위 형식을 반복하되 문서 사이를 '---' 한 줄로 구분하세요. 주제가 하나면 절대 나누지 마세요."""
TRANSCRIPTS = {
    "P1": ("사용자: 내일 오후 3시에 강남 미소치과 예약 일정 넣어줘\n비서: 9월 25일 오후 3시 미소치과 예약을 일정에 추가했어요.\n"
           "사용자: 아 미안, 치과 예약 4시로 바꿔줘\n비서: 미소치과 예약을 오후 4시로 옮겼어요.", ["치과", "4시"]),
    "P2": ("사용자: 금요일 마감인 발표 자료 목차 같이 짜자\n비서: 배경, 문제, 해결안, 일정 순서는 어떨까요?\n"
           "사용자: 좋아. 이 목차로 슬라이드 10장 구성 잡아줘\n비서: 표지 1장, 배경 2장, 문제 2장, 해결안 3장, 일정 1장, 마무리 1장이에요.", ["목차", "슬라이드"]),
    "P4": ("사용자: 엄마 생신 선물 꽃이 나을까 스카프가 나을까\n비서: 오래 쓰실 걸 원하면 스카프, 분위기는 꽃이 좋아요.\n"
           "사용자: 스카프로 할게. 주문하려고\n비서: 좋은 선택이에요. 생신 전에 도착하도록 주문해 주세요.", ["생신", "스카프"]),
}
for label, (tx, keys) in TRANSCRIPTS.items():
    out = ask(SUMMARY_SYSTEM, f"다음 대화를 요약하세요.\n\n{tx}")
    docs = [blk for blk in re.split(r"\n-{3,}\n", out) if re.search(r"제목\s*[:：]", blk)]
    has = all(k in out for k in keys)
    print(f"{label}: 문서 {len(docs)}편  핵심어 {'모두' if has else '누락'}\n{out.strip()[:300]}\n", flush=True)
