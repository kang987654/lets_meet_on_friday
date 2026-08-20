"""실험 2 — 일정 등록이 되묻기로 빠지는 문제를 프롬프트로 고친다.

실험 1 결과: add_memory 는 자연어 7/7 성공인데 add_schedule 은 3/4 실패.
실패 양상이 결정적이다 — 모델이 날짜를 **계산할 수 있는데도** 호출하지 않고 되묻는다:
  "다음 주 월요일은 2026년 8월 10일입니다. … 등록해 드릴까요?"
  "종료 시간도 알려주시면 더 정확하게 …"

원인 가설: 우리 시스템 프롬프트의
  "If you lack mandatory information to use a tool, DO NOT guess. Ask the user for clarification first."
가 상대 날짜("내일", "모레", "다음주 월요일")를 '없는 필수 정보'로 만들고, 선택 파라미터인
end_time 까지 필수로 착각하게 만든다.

변형을 4개 준비해 일정 4케이스(실패 3 + 성공 1)에 돌린다. 성공했던 케이스가 깨지지 않는지도
같이 본다.
"""

import sys

import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "gpu"

NOW = "2026-08-07 00:45"  # 금요일 기준 고정

CASES = [
    "내일 3시에 치과 예약 잡아줘",
    "모레 저녁 7시에 친구랑 약속 있어. 일정 추가해줘",
    "다음주 월요일 10시 팀 회의 등록해줘",
    "내일 오후 3시 치과 예약",           # 실험 1에서 유일하게 성공한 케이스
    "내 자전거 비밀번호는 1234야, 기억해줘",  # 메모리 회귀 확인
]

EXPECTED = ["add_schedule"] * 4 + ["add_memory"]


def prompt_v0():
    """현재 앱(0.8.6)."""
    return K.system_prompt(now=NOW)


def _base_lines(now):
    return [
        "[System]",
        "You are a personal assistant named Kosmos",
        "Always respond in Korean unless the user speaks another language.",
        f"[System Data] Current Time: {now}",
        "[Tool Usage Guidelines]",
        "For EVERY user request, first decide if one of your tools applies. "
        "If it does, you MUST call the tool. Do NOT merely promise or pretend — "
        "promising without calling is a failure.",
        '- The user asks you to remember something, or shares a fact/preference/password to keep — Korean triggers: "기억해", "기억해줘", "저장해줘", "메모해줘", "잊지 마": you MUST call `add_memory`.',
        '- The user asks to add an appointment, reservation, or event — Korean triggers: "예약", "약속", "일정 잡아줘", "일정 추가", "~하기로 했어": you MUST call `add_schedule`.',
        '- The user asks what is on their calendar — Korean triggers: "오늘 일정", "내일 일정", "스케줄 뭐 있어": you MUST call `get_schedule`.',
        '- The user asks a factual question you are not sure about — Korean triggers: "검색해줘", "찾아봐", "~가 뭐야?": call `search_wikipedia`.',
    ]


def prompt_v1(now=NOW):
    """상대 날짜를 스스로 계산하라고 명시 + 되묻기 조건을 좁힌다."""
    lines = _base_lines(now)
    lines += [
        "Resolve relative dates and times yourself from Current Time above — \"내일\"/tomorrow, \"모레\", \"다음주 월요일\", \"오후 3시\" are NOT missing information. Compute the absolute ISO 8601 value and call the tool. Never ask the user to restate a date you can compute.",
        "Optional parameters are never a reason to ask. Only ask the user when a REQUIRED parameter is genuinely absent (for example, an event with no title at all).",
        "Never alter numbers, dates, or proper nouns the user gave you — copy them exactly.",
        "If you do not need a tool, simply provide your final response in plain text.",
    ]
    return "\n".join(lines)


def prompt_v2(now=NOW):
    """v1 + 오늘 날짜를 요일까지 풀어 주고 파생 날짜를 미리 계산해 준다."""
    lines = _base_lines(now)
    lines += [
        "Today is Friday 2026-08-07. Therefore: 내일=2026-08-08, 모레=2026-08-09, 다음주 월요일=2026-08-10.",
        "Resolve relative dates and times yourself from the values above. They are NOT missing information — compute the absolute ISO 8601 value and call the tool.",
        "Optional parameters are never a reason to ask. Only ask when a REQUIRED parameter is genuinely absent.",
        "Never alter numbers, dates, or proper nouns the user gave you — copy them exactly.",
        "If you do not need a tool, simply provide your final response in plain text.",
    ]
    return "\n".join(lines)


def prompt_v3(now=NOW):
    """v1 + '되묻지 말고 즉시 호출' 을 가장 강하게 (승인 카드가 확인 관문이라는 근거 포함)."""
    lines = _base_lines(now)
    lines += [
        "Resolve relative dates and times yourself from Current Time above — \"내일\", \"모레\", \"다음주 월요일\", \"오후 3시\" are NOT missing information. Compute the absolute ISO 8601 value and call the tool.",
        "Optional parameters are never a reason to ask. Only ask when a REQUIRED parameter is genuinely absent.",
        "Do NOT ask the user to confirm before calling a tool. The app shows the user an approval card with your arguments, so the user always reviews and can correct them. Asking for confirmation in text is a failure.",
        "Never alter numbers, dates, or proper nouns the user gave you — copy them exactly.",
        "If you do not need a tool, simply provide your final response in plain text.",
    ]
    return "\n".join(lines)


VARIANTS = [
    ("v0-현재(0.8.6)", prompt_v0()),
    ("v1-날짜계산 허용", prompt_v1()),
    ("v2-v1+요일풀이", prompt_v2()),
    ("v3-v1+승인카드근거", prompt_v3()),
]


def main():
    lab = K.Lab(backend=BACKEND)
    scores = {}

    for label, sysmsg in VARIANTS:
        cfg = K.Config(label=label, system=sysmsg)
        print(f"\n{'=' * 100}\n{label}\n{'=' * 100}")
        ok = 0
        for expected, prompt in zip(EXPECTED, CASES):
            r = lab.run(prompt, cfg)
            good = r.called == expected
            ok += good
            line = f"  {'OK  ' if good else 'FAIL'} {r.seconds:5.1f}s  {prompt}"
            if r.tool_calls:
                line += f"\n            → {r.tool_calls[0].get('arguments')}"
            elif r.text:
                line += f'\n            → "{r.text[:110].replace(chr(10), " ")}"'
            print(line, flush=True)
        scores[label] = ok
        print(f"  ── {ok}/{len(CASES)}")

    print(f"\n{'=' * 100}\n요약")
    for label, ok in scores.items():
        print(f"  {ok}/{len(CASES)}  {label}")
    lab.close()


if __name__ == "__main__":
    main()
