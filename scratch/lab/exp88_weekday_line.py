"""exp88 — 날짜 줄에 다가오는 요일 날짜를 더하면 요일 일정이 맞는 날짜로 들어가는지 (0.41.11 M2, 사용자 결정 (나)).

exp87: "금요일 저녁 7시"·"토요일 아침 8시"에 "토요일이 며칠인지 알 수 없어요" 또는 지난 금요일로 넣었다. [System Data] 에는 오늘·내일·모레·다음주 월요일뿐.
판: P0 = C2(일정 규칙 한 문장)만 · W1 = C2 + 요일 줄 MM-DD · W3 = C2 + 요일 줄 yyyy-MM-dd. 오늘을 월요일(11-09)과 목요일(11-12) 두 번.
채점: add_schedule 이 불리고 start_time 이 기대 날짜·시각으로 시작하면 통과.
"""
import datetime as dt
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K
C2 = " Take the title from the user's words and never ask for end_time or memo."
KO_DAYS = "월화수목금토일"
EN_DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]


def base_line(today, en):
    nm = today + dt.timedelta(days=(7 - today.weekday()) or 7)
    f = lambda d: d.isoformat()  # noqa: E731
    wd = today.strftime("%A")
    if en:
        return (f"[System Data] Today={f(today)} ({wd}), Tomorrow={f(today + dt.timedelta(1))}, "
                f"Day after tomorrow={f(today + dt.timedelta(2))}, Next Monday={f(nm)}")
    return (f"[System Data] 오늘={f(today)} ({wd}), 내일={f(today + dt.timedelta(1))}, "
            f"모레={f(today + dt.timedelta(2))}, 다음주 월요일={f(nm)}")


def weekday_tail(today, en, full):
    days = [today + dt.timedelta(i) for i in range(1, 7)]
    fmt = (lambda d: d.isoformat()) if full else (lambda d: d.strftime("%m-%d"))
    if en:
        return ", Coming days=" + " ".join(f"{fmt(d)}({EN_DAYS[d.weekday()]})" for d in days)
    return ", 요일별=" + " ".join(f"{fmt(d)}({KO_DAYS[d.weekday()]})" for d in days)


def system(today, en, variant):
    s = R.SYS_EN_OFF if en else R.SYS_OFF
    old = base_line(dt.date(2026, 11, 9), en)
    assert old in s, old
    line = base_line(today, en) + ("" if variant == "P0" else weekday_tail(today, en, variant == "W3"))
    s = s.replace(old, line)
    rule = next(l for l in s.splitlines() if "`add_schedule`" in l and ("예약" in l or "appointment" in l))
    return s.replace(rule, rule + C2)


def nxt(today, wd, weeks=0):
    """today 이후(오늘 제외) 가장 가까운 wd 요일 + weeks 주."""
    d = today + dt.timedelta(days=((wd - today.weekday()) % 7) or 7)
    return d + dt.timedelta(weeks=weeks)


def cases(today):
    mon_next = today + dt.timedelta(days=(7 - today.weekday()) or 7)
    return [  # (첫 말, 이어서 말 또는 None, 영어?, 기대 시작)
        ("금요일 저녁 7시에 저녁 약속 잡아줘", None, False, f"{nxt(today, 4)}T19:00"),
        ("토요일 오후 2시에 친구 만나기로 했어", None, False, f"{nxt(today, 5)}T14:00"),
        ("이번 주 일요일 오전 11시 브런치 일정 넣어줘", None, False, f"{nxt(today, 6)}T11:00"),
        ("다음 주 수요일 3시 회의 잡아줘", None, False, f"{mon_next + dt.timedelta(2)}T15:00"),
        ("수요일 10시 치과 예약해줘", None, False, f"{nxt(today, 2)}T10:00"),
        ("저녁 약속 일정 넣어줘", "금요일 저녁 7시", False, f"{nxt(today, 4)}T19:00"),
        ("운동 일정 추가해줘", "토요일 아침 8시", False, f"{nxt(today, 5)}T08:00"),
        ("병원 예약 잡아줘", "다음 주 화요일 2시", False, f"{mon_next + dt.timedelta(1)}T14:00"),
        ("치과 예약 잡아줘", "내일 3시", False, f"{today + dt.timedelta(1)}T15:00"),
        ("Schedule lunch with Sam on Friday at noon", None, True, f"{nxt(today, 4)}T12:00"),
        ("Book a doctor's appointment", "Next Tuesday at 2pm", True, f"{mon_next + dt.timedelta(1)}T14:00"),
    ]


def one(history, user, sysm):
    hist = R.model_history((history or []) + [R.M("user", user)])[:-1]
    with K.create_chat_conversation(R.engine(), history=hist or None, tools=R.TOOLS_OFF, system_message=sysm) as conv:
        res = conv.send_message(K.with_turn_reminder(user))
        calls = res.get("tool_calls") or []
        if calls:
            return calls[0]["function"], None
        return None, R.text_of(res)


if __name__ == "__main__":
    for today in (dt.date(2026, 11, 9), dt.date(2026, 11, 12)):
        print(f"##### 오늘 {today} ({today.strftime('%A')})", flush=True)
        print("요일 줄 예:", weekday_tail(today, False, False), flush=True)
        for variant in ("P0", "W1", "W3"):
            ok = 0
            rows = []
            for first, follow, en, want in cases(today):
                sysm = system(today, en, variant)
                fn, text = one(None, first, sysm)
                if fn is None and follow:
                    fn, text = one([R.M("user", first), R.M("model", text)], follow, sysm)
                start = (fn.get("arguments") or {}).get("start_time", "") if fn and fn["name"] == "add_schedule" else ""
                hit = start.startswith(want)
                ok += hit
                rows.append(f"    {'O' if hit else 'X'} {first}{' → ' + follow if follow else ''} | 기대 {want} | {start or (text or '')[:50]}")
            print(f"== {variant} {ok}/{len(rows)}", flush=True)
            print("\n".join(rows), flush=True)
