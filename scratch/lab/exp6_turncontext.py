"""exp6 — 시각·기억 블록을 시스템 지시에서 **사용자 턴**으로 옮겨도 회귀가 없는지 확인.

exp5 에서 대화 재생성이 턴당 3초 이상을 프리필에만 쓴다는 것을 확인했고, 그 원인은
시스템 지시 안의 휘발성 값(분 단위 시각, RAG 로 검색된 기억)이었다. 앱은 그 블록을
사용자 턴 앞머리로 옮겼다 — 그런데 exp1~exp3 의 프롬프트 실험은 모두 **시스템 지시에
시각이 있는** 조건에서 검증한 것이다. 위치를 바꾼 뒤에도 툴 호출과 날짜 계산이 유지되는지는
따로 확인해야 한다.

  A) 시각 블록을 시스템 지시에 (이전 앱)
  B) 시각 블록을 사용자 턴 앞머리에 (현재 앱, 지시 문구도 "each user message begins with" 로 교체)
"""

import sys

import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "gpu"

TIME_BLOCK = (
    "[System Data] Current Time: 2026-08-07 00:45 (Friday)\n"
    "[System Data] 오늘=2026-08-07, 내일=2026-08-08, 모레=2026-08-09, 다음주 월요일=2026-08-10"
)

TOOL_RULES = [
    "[Tool Usage Guidelines]",
    "For EVERY user request, first decide if one of your tools applies. "
    "If it does, you MUST call the tool. Do NOT merely promise or pretend — "
    "promising without calling is a failure.",
    '- The user asks you to remember something, or shares a fact/preference/password to keep — Korean triggers: "기억해", "기억해줘", "저장해줘", "메모해줘", "잊지 마": you MUST call `add_memory`.',
    '- The user asks to add an appointment, reservation, or event — Korean triggers: "예약", "약속", "일정 잡아줘", "일정 추가", "~하기로 했어": you MUST call `add_schedule`.',
    '- The user asks what is on their calendar — Korean triggers: "오늘 일정", "내일 일정", "스케줄 뭐 있어": you MUST call `get_schedule`.',
    '- The user asks a factual question you are not sure about — Korean triggers: "검색해줘", "찾아봐", "~가 뭐야?": call `search_wikipedia`.',
]

TAIL_RULES = [
    "Optional parameters are never a reason to ask. Only ask the user when a REQUIRED parameter is genuinely absent.",
    "Never alter numbers, dates, or proper nouns the user gave you — copy them exactly.",
    "If you do not need a tool, simply provide your final response in plain text.",
]

HEAD = [
    "[System]",
    "You are a personal assistant named Kosmos",
    "Always respond in Korean unless the user speaks another language.",
]


def system_a():
    """이전 앱: 시각이 시스템 지시 안에 있고 규칙이 'above' 를 가리킨다."""
    return "\n".join(
        HEAD
        + [TIME_BLOCK]
        + TOOL_RULES
        + [
            'Resolve relative dates and times yourself from the [System Data] values above — '
            '"내일", "모레", "다음주 월요일", "오후 3시" are NOT missing information. '
            "Compute the absolute ISO 8601 value and call the tool. "
            "Never ask the user to restate a date you can compute."
        ]
        + TAIL_RULES
    )


def system_b():
    """현재 앱: 시각이 사용자 턴에 오고 규칙이 그 위치를 가리킨다."""
    return "\n".join(
        HEAD
        + TOOL_RULES
        + [
            "Each user message begins with a [System Data] block giving the current time and "
            'pre-computed dates. Resolve relative dates and times yourself from those values — '
            '"내일", "모레", "다음주 월요일", "오후 3시" are NOT missing information. '
            "Compute the absolute ISO 8601 value and call the tool. "
            "Never ask the user to restate a date you can compute."
        ]
        + TAIL_RULES
    )


def get_schedule_narrowed(date: str) -> dict:
    """사용자의 캘린더 일정을 조회한다.

    Args:
        date: 조회 범위. 반드시 'today' 또는 'week' 중 하나만 쓴다. 내일·모레·이번주·다음주처럼 오늘이 아닌 날을 물으면 'week' 를 쓴다. 다른 값은 쓰지 않는다.
    """
    raise AssertionError("실행되지 않아야 한다")


TOOLS = [K.add_schedule, get_schedule_narrowed, K.add_memory, K.search_wikipedia]

CASES = [
    ("add_memory", "내 자전거 비밀번호는 1234야, 기억해줘", "content", "1234"),
    ("add_memory", "우리집 와이파이 비번 aB3x9k 저장해줘", "content", "aB3x9k"),
    ("add_memory", "나 커피보다 녹차를 더 좋아해. 기억해둬", None, None),
    ("add_schedule", "내일 3시에 치과 예약 잡아줘", "start_time", "2026-08-08T15:00"),
    ("add_schedule", "모레 저녁 7시에 친구랑 약속 있어. 일정 추가해줘", "start_time", "2026-08-09T19:00"),
    ("add_schedule", "다음주 월요일 10시 팀 회의 등록해줘", "start_time", "2026-08-10T10:00"),
    ("add_schedule", "8월 20일 오후 2시 병원 예약", "start_time", "2026-08-20T14:00"),
    ("get_schedule", "오늘 일정 뭐 있어?", "date", "today"),
    ("get_schedule", "내일 스케줄 알려줘", "date", "week"),
    ("search_wikipedia", "세종대왕이 누구야? 검색해줘", "topic", "세종대왕"),
    ("search_wikipedia", "광합성이 뭐야?", "topic", "광합성"),
    (None, "안녕! 오늘 기분 어때?", None, None),
    (None, "고맙습니다", None, None),
    (None, "내 자전거 비밀번호 뭐였지?", None, None),
]


def run_variant(lab, label, system, prefix_time):
    cfg = K.Config(label=label, tools=TOOLS, system=system)
    ok = 0
    print(f"\n{'=' * 90}\n[{label}]\n{'=' * 90}")
    for expected, text, key, want in CASES:
        prompt = f"{TIME_BLOCK}\n\n{text}" if prefix_time else text
        r = lab.run(prompt, cfg)
        good = r.called == (expected or "-")
        note = ""
        if good and key and want:
            got = str(r.arg(key) or "")
            if want not in got:
                good = False
                note = f"   인자 불일치: {key}={got!r} (기대에 {want!r} 포함)"
        ok += good
        line = f"  {'OK  ' if good else 'FAIL'} {r.seconds:5.1f}s  {text}"
        if r.tool_calls:
            line += f"\n            → {r.tool_calls[0].get('arguments')}"
        elif r.text:
            line += f'\n            → "{r.text[:90].replace(chr(10), " ")}"'
        print(line + note, flush=True)
    print(f"\n  ── {ok}/{len(CASES)}")
    return ok


def main():
    lab = K.Lab(backend=BACKEND)
    a = run_variant(lab, "A) 시각을 시스템 지시에 (이전 앱)", system_a(), prefix_time=False)
    b = run_variant(lab, "B) 시각을 사용자 턴에 (현재 앱)", system_b(), prefix_time=True)
    print(f"\n=== 결과: A {a}/{len(CASES)}  vs  B {b}/{len(CASES)} ===")
    lab.close()


if __name__ == "__main__":
    main()
