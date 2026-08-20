"""실험 3 — (a) get_schedule 의 date 값 교정, (b) 최종 프롬프트 전체 회귀.

배경 (a): 실험 1에서 "내일 스케줄 알려줘" → `get_schedule(date='tomorrow')`.
그런데 앱 `GetScheduleToolExecutor` 는 date 에 "week" 가 없으면 **무조건 TODAY** 로 떨어진다
(도메인이 TODAY/WEEK 두 범위만 지원). 즉 내일을 물으면 오늘 일정이 조용히 나오는 버그다.
도메인을 늘리지 않고 고치려면 툴 설명으로 값을 좁혀야 한다 — 내일/이번주 질문은 'week' 로.

배경 (b): 실험 2에서 v1(상대 날짜 계산 허용) 이 2/5 → 5/5 를 만들었고, v2(요일 풀이)만
"다음주 월요일"을 정확히 계산했다(v1/v3 는 8/17 로 한 주 틀림). 둘을 합친 최종 프롬프트가
메모리·일정·조회·검색·대조군 전체에서 회귀 없이 동작하는지 확인한다.
"""

import sys

import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "gpu"

NOW = "2026-08-07 00:45"


# ---------------------------------------------------------------- (a) 툴 설명 변형

def get_schedule_narrowed(date: str) -> dict:
    """사용자의 캘린더 일정을 조회한다.

    Args:
        date: 조회 범위. 반드시 'today' 또는 'week' 중 하나만 쓴다. 내일·모레·이번주·다음주처럼 오늘이 아닌 날을 물으면 'week' 를 쓴다. 다른 값은 쓰지 않는다.
    """
    raise AssertionError("실행되지 않아야 한다")


# ---------------------------------------------------------------- 최종 프롬프트

def final_prompt(now=NOW, today_line="Today is Friday 2026-08-07. Therefore: 내일=2026-08-08, 모레=2026-08-09, 다음주 월요일=2026-08-10."):
    """v1(날짜 계산 허용) + v2(요일·파생 날짜 풀이). 앱 PromptAssembler 에 이식할 후보."""
    return "\n".join([
        "[System]",
        "You are a personal assistant named Kosmos",
        "Always respond in Korean unless the user speaks another language.",
        f"[System Data] Current Time: {now}",
        today_line,
        "[Tool Usage Guidelines]",
        "For EVERY user request, first decide if one of your tools applies. "
        "If it does, you MUST call the tool. Do NOT merely promise or pretend — "
        "promising without calling is a failure.",
        '- The user asks you to remember something, or shares a fact/preference/password to keep — Korean triggers: "기억해", "기억해줘", "저장해줘", "메모해줘", "잊지 마": you MUST call `add_memory`.',
        '- The user asks to add an appointment, reservation, or event — Korean triggers: "예약", "약속", "일정 잡아줘", "일정 추가", "~하기로 했어": you MUST call `add_schedule`.',
        '- The user asks what is on their calendar — Korean triggers: "오늘 일정", "내일 일정", "스케줄 뭐 있어": you MUST call `get_schedule`.',
        '- The user asks a factual question you are not sure about — Korean triggers: "검색해줘", "찾아봐", "~가 뭐야?": call `search_wikipedia`.',
        "Resolve relative dates and times yourself from the values above — \"내일\", \"모레\", \"다음주 월요일\", \"오후 3시\" are NOT missing information. Compute the absolute ISO 8601 value and call the tool. Never ask the user to restate a date you can compute.",
        "Optional parameters are never a reason to ask. Only ask the user when a REQUIRED parameter is genuinely absent.",
        "Never alter numbers, dates, or proper nouns the user gave you — copy them exactly.",
        "If you do not need a tool, simply provide your final response in plain text.",
    ])


# ---------------------------------------------------------------- 케이스

DATE_CASES = [
    ("today", "오늘 일정 뭐 있어?"),
    ("week", "내일 스케줄 알려줘"),
    ("week", "이번주 일정 보여줘"),
    ("week", "모레 뭐 있지?"),
]

FULL_CASES = [
    # (기대 툴, 입력, 검증할 인자 키, 기대 인자값 부분문자열 또는 None)
    ("add_memory", "내 자전거 비밀번호는 1234야, 기억해줘", "content", "1234"),
    ("add_memory", "우리집 와이파이 비번 aB3x9k 저장해줘", "content", "aB3x9k"),
    ("add_memory", "나 커피보다 녹차를 더 좋아해. 기억해둬", None, None),
    ("add_schedule", "내일 3시에 치과 예약 잡아줘", "start_time", "2026-08-08T15:00"),
    ("add_schedule", "모레 저녁 7시에 친구랑 약속 있어. 일정 추가해줘", "start_time", "2026-08-09T19:00"),
    ("add_schedule", "다음주 월요일 10시 팀 회의 등록해줘", "start_time", "2026-08-10T10:00"),
    ("add_schedule", "8월 20일 오후 2시 병원 예약", "start_time", "2026-08-20T14:00"),
    ("get_schedule", "오늘 일정 뭐 있어?", None, None),
    ("get_schedule", "내일 스케줄 알려줘", None, None),
    ("search_wikipedia", "세종대왕이 누구야? 검색해줘", "topic", "세종대왕"),
    ("search_wikipedia", "광합성이 뭐야?", "topic", "광합성"),
    (None, "안녕! 오늘 기분 어때?", None, None),
    (None, "고맙습니다", None, None),
    (None, "내 자전거 비밀번호 뭐였지?", None, None),  # 조회는 툴 없이 문맥에서
]


def main():
    lab = K.Lab(backend=BACKEND)

    # ---- (a) date 값 좁히기
    print("=" * 100)
    print("(a) get_schedule date 값 — 현재 설명 vs 좁힌 설명")
    print("=" * 100)
    for label, tools in [
        ("현재", K.ALL_TOOLS),
        ("좁힘", [K.add_schedule, get_schedule_narrowed, K.add_memory, K.search_wikipedia]),
    ]:
        cfg = K.Config(label=label, tools=tools, system=final_prompt())
        print(f"\n[{label}]")
        for expected, prompt in DATE_CASES:
            r = lab.run(prompt, cfg)
            got = r.arg("date")
            # 앱 executor 는 'week' 포함 여부만 본다 → 그 관점으로 판정
            effective = "week" if (got or "").lower().find("week") >= 0 else "today"
            good = effective == expected
            print(f"  {'OK  ' if good else 'FAIL'} {prompt:<22} date={got!r} → 앱 해석={effective} (기대 {expected})", flush=True)

    # ---- (b) 최종 프롬프트 전체 회귀
    print(f"\n{'=' * 100}")
    print("(b) 최종 프롬프트 전체 회귀 (좁힌 get_schedule 설명 적용)")
    print("=" * 100)
    cfg = K.Config(
        label="final",
        tools=[K.add_schedule, get_schedule_narrowed, K.add_memory, K.search_wikipedia],
        system=final_prompt(),
    )
    ok = 0
    for expected, prompt, key, want in FULL_CASES:
        r = lab.run(prompt, cfg)
        good = r.called == (expected or "-")
        note = ""
        if good and key and want:
            got = str(r.arg(key) or "")
            if want not in got:
                good = False
                note = f"  인자 불일치: {key}={got!r} (기대에 {want!r} 포함)"
        ok += good
        line = f"  {'OK  ' if good else 'FAIL'} {r.seconds:5.1f}s  {prompt}"
        if r.tool_calls:
            line += f"\n            → {r.tool_calls[0].get('arguments')}"
        elif r.text:
            line += f'\n            → "{r.text[:100].replace(chr(10), " ")}"'
        print(line + note, flush=True)
    print(f"\n  ── {ok}/{len(FULL_CASES)}")
    lab.close()


if __name__ == "__main__":
    main()
