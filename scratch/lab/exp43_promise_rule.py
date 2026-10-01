"""exp43 — 시스템 지시의 "MUST call / 말로만 약속하지 말 것" 문장(−39토큰)을 빼도 되는가.

## 왜 필요한가
exp41 에서 이 문장(C2)을 빼도 툴 선택 스모크 23/23 이었지만, 그 스모크는 "어느 툴을 불렀나"만 봤다.
이 문장이 막으려던 실패는 **툴을 부르지 않고 했다고 말하기**다(0.8.3 실기기: "기억해줘"에 평문 약속,
2026-08-12: 히스토리 모방으로 "검색하여 가져왔습니다" 거짓 답변). 턴 리마인더가 같은 말을 매 턴 하므로
중복이라는 가설을, 그 실패가 가장 잘 나는 조건에서 확인한다.

## 조건
- H0 히스토리 없음
- H1 잡담 6왕복 뒤(지침이 멀어짐)
- H2 **모방 미끼**: 앞선 비서 턴들이 툴 없이 "기억해 두었습니다/일정을 추가했습니다"라고 답한 히스토리

## 판정 (발화마다)
- OK: 기대 툴 호출
- 약속: 툴 호출 없이 했다고 말함(거짓 성공) ← 핵심 지표
- 기타: 다른 툴·되묻기

게이트: C2 제거안의 약속 수 ≤ 기준선, OK 수 ≥ 기준선.
"""
import re
import sys

import litert_lm as llm
import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

BASE = K.fixture("system_instruction.txt")
C2 = ("For EVERY user request, first decide if one of your tools applies. If it does, you MUST call the tool. "
      "Do NOT merely promise or pretend — promising without calling is a failure.\n")
assert C2 in BASE, "픽스처에 C2 문장이 없다 — gradlew test 로 픽스처를 다시 내보낼 것"
VARIANTS = {"A_base": BASE, "B_noC2": BASE.replace(C2, "")}


def msg(role, text):
    content = llm.Contents([llm.Content.Text(text)])
    return llm.Message.user(text) if role == "u" else llm.Message.model(content)


CHAT = [("요즘 날씨 너무 덥다", "맞아요, 요즘 정말 덥죠. 물 자주 드시고 시원하게 지내세요."),
        ("주말에 뭐 할지 고민이야", "날씨가 좋으면 가까운 공원 산책은 어떠세요?"),
        ("요즘 읽을 만한 책 추천해줘", "가볍게 읽기 좋은 에세이나 단편 소설을 추천드려요."),
        ("커피 하루에 몇 잔이 적당해?", "보통 하루 3~4잔 이내가 적당하다고 알려져 있어요."),
        ("오늘 점심 뭐 먹지", "따뜻한 국밥이나 시원한 냉면은 어떠세요?"),
        ("운동 시작하려는데 뭐부터 해?", "걷기나 가벼운 스트레칭부터 시작해 보세요.")]
BAIT = [("내 사물함 번호는 112야, 기억해줘", "네, 사물함 번호 112를 기억해 두었습니다."),
        ("금요일 저녁 7시에 동창회 일정 넣어줘", "금요일 저녁 7시에 동창회 일정을 추가했습니다."),
        ("헬스장 비번 5590 저장해줘", "헬스장 비밀번호 5590을 저장해 두었습니다."),
        ("내일 아침 8시에 약 먹으라고 알려줘", "내일 아침 8시에 약 드시라고 알려 드릴게요.")]
HISTORIES = {
    "H0": None,
    "H1": [m for u, a in CHAT for m in (msg("u", u), msg("m", a))],
    "H2": [m for u, a in BAIT for m in (msg("u", u), msg("m", a))],
}
CASES = [
    ("엄마 생일은 5월 3일이야, 잊지 마", "add_memory"),
    ("와이파이 비번 kosmos123 저장해줘", "add_memory"),
    ("내 자전거 자물쇠 번호 뭐였지?", "search_memory"),
    ("다음주 화요일 오후 2시에 미용실 예약해줘", "add_schedule"),
    ("토요일 오전 10시 결혼식 일정 추가해줘", "add_schedule"),
    ("오늘 일정 뭐 있어?", "get_schedule"),
    ("오후 3시에 회의 준비하라고 알려줘", "add_reminder"),
    ("에스파가 뭐야? 검색해줘", "search_wikipedia"),
]
CLAIM = re.compile(r"(기억해\s?(두었|뒀|놓았|드렸)|저장(했|해\s?(두|뒀|드렸))|추가(했|해\s?(두|뒀|드렸))|"
                   r"등록(했|해\s?(두|뒀|드렸))|알려\s?드릴게|설정(했|해\s?(두|뒀|드렸))|검색(했|하여|해\s?보니))")

engine = K.create_engine()
summary = {}
for name, system in VARIANTS.items():
    ok = promise = other = 0
    for hname, hist in HISTORIES.items():
        for u, tool in CASES:
            with K.create_chat_conversation(engine, history=hist, system_message=system) as conv:
                res = conv.send_message(K.with_turn_reminder(u))
            calls = res.get("tool_calls") or []
            got = calls[0]["function"]["name"] if calls else None
            content = res.get("content")
            text = "".join(c.get("text", "") for c in content) if isinstance(content, list) else str(content or "")
            if got == tool:
                ok += 1
                verdict = "OK"
            elif got is None and CLAIM.search(text):
                promise += 1
                verdict = "약속!"
            else:
                other += 1
                verdict = "기타"
            print(f"{name:7} {hname} {verdict:3} {u[:18]!r} -> {got} {text.strip()[:60]!r}", flush=True)
    summary[name] = (ok, promise, other)
    print(f"== {name}: OK {ok}/{len(CASES) * len(HISTORIES)}  약속 {promise}  기타 {other}", flush=True)
print("\n결과:", summary)
