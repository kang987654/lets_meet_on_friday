"""exp8 — 프리필 재사용과 조회 정확도를 함께 만족하는 배치를 찾는다.

exp7 결론: 조회 질문("내 자전거 비밀번호 뭐였지?")이 저장 호출로 새는 원인은 규칙 문구가
아니라 **`[System Data]` 블록이 사용자 턴 앞머리에 오는 것** 자체였다.
  C) 시각을 시스템 지시에 → 3/3 정상
  B) 시각을 사용자 턴에   → 1/3 (문구는 새것)
  D) 시각을 사용자 턴에   → 2/3 (문구는 옛것)

그런데 시각을 시스템 지시에 두면 분 단위로 바뀌어 대화가 매 턴 재생성된다(exp5: 턴당 +3초).
해법 가설: **시스템 지시에 두되 하루 단위로 만든다.** 일정 등록에 실제로 필요한 것은 파생
날짜(오늘·내일·모레·다음주 월요일)와 요일이고, 그것들은 하루에 한 번만 바뀐다. 분 단위
시계(`HH:mm`)만 빼면 시스템 지시가 세션 내내 고정된다.

  F) 시스템 지시에 날짜 블록(시계 없음) + 턴 문맥은 RAG 기억만
  G) F 와 같지만 턴 문맥 없음 (RAG 없는 세션)

전체 14케이스 + 조회 3케이스로 회귀를 본다.
"""

import sys

import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "gpu"

# 하루 단위 — 분 단위 시계가 없다.
DAY_BLOCK = (
    "[System Data] Today: 2026-08-07 (Friday)\n"
    "[System Data] 오늘=2026-08-07, 내일=2026-08-08, 모레=2026-08-09, 다음주 월요일=2026-08-10"
)

# 비교용 — exp6 의 A(분 단위, 시스템 지시)
MINUTE_BLOCK = (
    "[System Data] Current Time: 2026-08-07 00:45 (Friday)\n"
    "[System Data] 오늘=2026-08-07, 내일=2026-08-08, 모레=2026-08-09, 다음주 월요일=2026-08-10"
)

RAG_BLOCK = (
    "[Context / Knowledge]\n"
    "다음은 사용자에 대한 과거 기억(Memory)입니다. 답변에 필요하다면 적극 활용하세요:\n"
    "- 자전거 비밀번호는 1234\n"
    "- 커피보다 녹차를 더 좋아함"
)

HEAD = [
    "[System]",
    "You are a personal assistant named Kosmos",
    "Always respond in Korean unless the user speaks another language.",
]

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

DATE_RULE = (
    'Resolve relative dates and times yourself from the [System Data] values above — '
    '"내일", "모레", "다음주 월요일", "오후 3시" are NOT missing information. '
    "Compute the absolute ISO 8601 value and call the tool. "
    "Never ask the user to restate a date you can compute."
)

TAIL_RULES = [
    "Optional parameters are never a reason to ask. Only ask the user when a REQUIRED parameter is genuinely absent.",
    "Never alter numbers, dates, or proper nouns the user gave you — copy them exactly.",
    "If you do not need a tool, simply provide your final response in plain text.",
]


def system(time_block):
    return "\n".join(HEAD + [time_block] + TOOL_RULES + [DATE_RULE] + TAIL_RULES)


def get_schedule_narrowed(date: str) -> dict:
    """사용자의 캘린더 일정을 조회한다.

    Args:
        date: 조회 범위. 반드시 'today' 또는 'week' 중 하나만 쓴다. 내일·모레·이번주·다음주처럼 오늘이 아닌 날을 물으면 'week' 를 쓴다. 다른 값은 쓰지 않는다.
    """
    raise AssertionError("실행되지 않아야 한다")


# [WHY] `tool_from_function` 은 함수 이름을 툴 이름으로 쓴다 — 이름을 되돌리지 않으면
# 판정이 실제 실패가 아닌데도 FAIL 로 찍힌다(exp6 에서 겪었다).
get_schedule_narrowed.__name__ = "get_schedule"
get_schedule_narrowed.__qualname__ = "get_schedule"

TOOLS = [K.add_schedule, get_schedule_narrowed, K.add_memory, K.search_wikipedia]

# (기대 툴 또는 None, 입력, 검증 인자 키, 기대 부분문자열)
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
    (None, "내가 뭘 좋아한다고 했지?", None, None),
    (None, "아까 저장한 거 뭐였어?", None, None),
]

VARIANTS = [
    ("A  분 단위 시각, 시스템 지시 (이전 앱)", MINUTE_BLOCK, False),
    ("F  하루 단위 날짜, 시스템 지시 + 턴에 RAG", DAY_BLOCK, True),
    ("G  하루 단위 날짜, 시스템 지시 + 턴 문맥 없음", DAY_BLOCK, False),
]


def main():
    lab = K.Lab(backend=BACKEND)
    summary = []
    for label, time_block, with_rag in VARIANTS:
        cfg = K.Config(label=label, tools=TOOLS, system=system(time_block))
        print(f"\n{'=' * 94}\n[{label}]\n{'=' * 94}")
        ok = 0
        for expected, text, key, want in CASES:
            prompt = f"{RAG_BLOCK}\n\n{text}" if with_rag else text
            r = lab.run(prompt, cfg)
            good = r.called == (expected or "-")
            note = ""
            if good and key and want:
                got = str(r.arg(key) or "")
                if want not in got:
                    good = False
                    note = f"   인자 불일치: {key}={got!r} (기대에 {want!r})"
            ok += good
            line = f"  {'OK  ' if good else 'FAIL'} {r.seconds:5.1f}s  {text}"
            if r.tool_calls:
                line += f"\n            → {r.called} {r.tool_calls[0].get('arguments')}"
            elif r.text:
                line += f'\n            → "{r.text[:100].replace(chr(10), " ")}"'
            print(line + note, flush=True)
        print(f"\n  ── {ok}/{len(CASES)}")
        summary.append((label, ok))

    print(f"\n{'=' * 94}\n=== 요약 ===")
    for label, ok in summary:
        print(f"  {ok}/{len(CASES)}  {label}")
    lab.close()


if __name__ == "__main__":
    main()
