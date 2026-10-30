"""exp68b — 0.39.3 M1: ② get_schedule date 설명 축약이 'tomorrow' 를 낳았다(exp68 smoke). 제약을 남긴 짧은 판(②′)을 원래·②와 비교.
나머지 축약(③④⑤)은 exp68 그대로 둔 상태에서 일정 조회 질문 6개 × 판 3개 — date 가 'today'/'week' 인가."""
import sys
import kosmos_lab as K
ORIG = K.get_schedule.__doc__
import exp68_trim as X  # noqa: E402,F401 — ③④⑤ 적용(② 포함)
R = X.R
SHORT = K.get_schedule.__doc__
PRIME = """사용자의 캘린더 일정을 조회한다.

    Args:
        date: 반드시 'today' 또는 'week'. 오늘이 아닌 날(내일·모레·다음주)은 'week'. 다른 값은 쓰지 않는다.
    """
Q = ["내일 일정 뭐 있어?", "모레 일정 알려줘", "이번 주 일정 뭐 있어?", "다음 주 일정 있어?", "오늘 일정 뭐야?", "토요일에 뭐 있지?"]
for name, doc in (("원래", ORIG), ("②", SHORT), ("②′", PRIME)):
    K.get_schedule.__doc__ = doc
    got = []
    for q in Q:
        _, _, _, first = R.turn(None, q)
        got.append((first or {}).get("arguments", {}).get("date") if first and first["name"] == "get_schedule" else (first or {}).get("name"))
    ok = sum(d in ("today", "week") for d in got)
    print(f"{name}: {ok}/{len(Q)} {got}", flush=True)
print("②′ 설명 토큰", len(R.engine().tokenize("반드시 'today' 또는 'week'. 오늘이 아닌 날(내일·모레·다음주)은 'week'. 다른 값은 쓰지 않는다.")), flush=True)
