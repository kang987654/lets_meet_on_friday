"""exp95 — 앞부분 고정 다이어트 후보 (0.42.1 M1·M2).

[1] 지금 시스템 지시의 줄마다 토큰(engine.tokenize) — 큰 것부터.
[2] 후보: base(지금) · A(트리거 줄 압축 + D3 혼동 문구) · B(A + 날짜 줄의 요일별 엿새 빼기). 앞부분 토큰(대화 token_count − 답 − 입력).
[3] 툴 선택 32문항 × 2회(exp94 [2]) — 틀림 수, D3 정답 기준(약속·예약 + 언제/몇 시였지 → get_schedule, 일정 + 기억해 둬 → add_schedule).
[4] 요일 두 턴(목요일 기준 "치과 예약 잡아줘" → "수요일 오전 11시") — 가드 없이 날짜가 맞는 수(B 가 요일별 줄을 빼도 되는지).
"""
import datetime as dt
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import run as R  # noqa: E402
import exp94_tool_routing as X  # noqa: E402

K = R.K
BASE = R.SYS_OFF
TRIG_START = "For EVERY user request"
TRIG_END = "Compute dates and times yourself"

NEW_TRIGGERS = """For EVERY user request, first decide if one of your tools applies. If it does, you MUST call the tool. Do NOT merely promise or pretend — promising without calling is a failure.
- Remember requests ("기억해줘", "저장해줘", "메모해줘", "잊지 마"): call `add_memory`. If the user only shares a fact, confirm it and ask "메모해 둘까요?" — never say you will remember it.
- Questions about what the user told you ("뭐였지", "내가 알려준", "기억나"): call `search_memory` with a short noun keyword. A lookup — never `add_memory`.
- Adding an appointment, reservation, or event ("예약", "약속", "일정 잡아줘", "~하기로 했어"), even if they say "기억해 둬": call `add_schedule`. Take the title from the user's words; never ask for end_time or memo.
- Calendar questions, including when an appointment or reservation is ("오늘 일정", "약속 언제였지", "치과 몇 시였지"): call `get_schedule`.
- Reminders at a time ("3시에 알려줘", "리마인드"): call `add_reminder`.
- Listing reminders ("알림 뭐 있어"): call `list_reminders`.
"""


def variant(name, base=BASE):
    if name == "base":
        return base
    i, j = base.index(TRIG_START), base.index(TRIG_END)
    s = base[:i] + NEW_TRIGGERS + base[j:]
    if name == "A2":
        # [WHY] A 에서 "내일 회의 있는 거 기억해 둬" 가 여전히 메모로 감 — 메모 줄에 일정 제외를 넣고, 일정 줄과 겹치는 Optional 줄을 빼고, 날짜 계산 줄을 줄인다.
        s = s.replace("call `add_memory`. If the user", "call `add_memory` (an appointment with a date or time goes to `add_schedule`). If the user")
        s = s.replace("Optional parameters are never a reason to ask. Only ask the user when a REQUIRED parameter is genuinely absent.\n", "")
        s = s.replace('Compute dates and times yourself from [System Data] above ("내일", "모레", "오후 3시" are not missing). A time with no date word means today.',
                      'Compute dates and times from [System Data] ("내일", "오후 3시" are not missing); a time with no date word means today.')
    if name == "B":
        s = re.sub(r", 요일별=[^\n]*", "", s)
    return s


def preface(sysm):
    eng = R.engine()
    with K.create_chat_conversation(eng, history=None, tools=R.TOOLS_OFF, system_message=sysm) as conv:
        msg = "안녕"
        res = conv.send_message(msg)
        return conv.token_count - len(eng.tokenize(R.text_of(res))) - len(eng.tokenize(msg))


def first_call(q, sysm):
    with K.create_chat_conversation(R.engine(), history=None, tools=R.TOOLS_OFF, system_message=sysm) as conv:
        cs = conv.send_message(K.with_turn_reminder(q)).get("tool_calls") or []
        return cs[0]["function"]["name"] if cs else None


def weekday_two_turn(sysm):
    """목요일(11-12) 기준 — 가드 없이 2턴 add_schedule 날짜가 수요일(11-18)인가."""
    ok = n = 0
    for u1, u2, want in (("치과 예약 잡아줘", "수요일 오전 11시", "2026-11-18"), ("건강검진 예약 넣어줘", "수요일 아침 9시", "2026-11-18"),
                         ("회식 일정 잡아줘", "금요일 저녁 7시", "2026-11-13"), ("운동 일정 추가해줘", "토요일 아침 8시", "2026-11-14")):
        for _ in (1, 2):
            with K.create_chat_conversation(R.engine(), history=None, tools=R.TOOLS_OFF, system_message=sysm) as conv:
                a1 = R.text_of(conv.send_message(K.with_turn_reminder(u1)))
            with K.create_chat_conversation(R.engine(), history=[R.M("user", u1), R.M("model", a1)], tools=R.TOOLS_OFF, system_message=sysm) as conv:
                cs = conv.send_message(K.with_turn_reminder(u2)).get("tool_calls") or []
            st = str((cs[0]["function"].get("arguments") or {}).get("start_time", "")) if cs else ""
            n += 1
            ok += st.startswith(want)
    return ok, n


def main():
    eng = R.engine()
    print("=== [1] 지금 시스템 지시 줄별 토큰(큰 것부터) ===", flush=True)
    for t, line in sorted(((len(eng.tokenize(l)), l) for l in BASE.splitlines() if l.strip()), reverse=True)[:12]:
        print(f"  {t:4d} | {line[:90]}", flush=True)
    thu = R._sys_for_date(dt.date(2026, 11, 12), False)
    for name in ([a for a in sys.argv[1:]] or ("base", "A", "B")):
        sysm = variant(name)
        wrong = []
        for q, want in X.CASES:
            for _ in (1, 2):
                got = first_call(q, sysm)
                if got != want:
                    wrong.append(f"{q}→{got}")
        wk, wn = weekday_two_turn(variant(name, thu))
        print(f"\n=== {name}: 앞부분 {preface(sysm)} · 시스템 지시 {len(eng.tokenize(sysm))} · 툴 선택 틀림 {len(wrong)}/{len(X.CASES) * 2} · 요일 두 턴 날짜 맞음 {wk}/{wn}", flush=True)
        for w in sorted(set(wrong)):
            print(f"    틀림 {w} ×{wrong.count(w)}", flush=True)


if __name__ == "__main__":
    main()
