"""exp7 — exp6 에서 유일하게 갈린 케이스의 원인 분리.

exp6 결과에서 "내 자전거 비밀번호 뭐였지?"(조회 질문)가
  A) 시각을 시스템 지시에  → 말로 답함 (기대대로)
  B) 시각을 사용자 턴에    → `add_memory("자전거 비밀번호는 8282")` 를 호출 (few-shot 예시의 숫자를 끌어옴)
로 갈렸다. B 는 두 가지를 동시에 바꿨으므로 어느 쪽이 원인인지 모른다:
  ① 시각 블록의 위치        ② 규칙 문구("values above" → "each user message begins with")

또 앱의 실제 조건은 조회 질문에 RAG 로 검색된 기억이 턴 문맥에 함께 온다는 것이다.
exp6 의 B 에는 그것이 없었으므로 앱보다 불리한 조건이었다.

변형:
  B  새 문구 + 시각을 사용자 턴에            (현재 앱)
  C  새 문구 + 시각을 시스템 지시에          (문구만 바뀜)
  D  옛 문구 + 시각을 사용자 턴에            (위치만 바뀜)
  E  현재 앱 + 턴 문맥에 RAG 기억 포함       (앱의 실제 조건)
"""

import sys

import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "gpu"

TIME_BLOCK = (
    "[System Data] Current Time: 2026-08-07 00:45 (Friday)\n"
    "[System Data] 오늘=2026-08-07, 내일=2026-08-08, 모레=2026-08-09, 다음주 월요일=2026-08-10"
)

RAG_BLOCK = (
    "\n\n[Context / Knowledge]\n"
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

RULE_OLD = (
    'Resolve relative dates and times yourself from the [System Data] values above — '
    '"내일", "모레", "다음주 월요일", "오후 3시" are NOT missing information. '
    "Compute the absolute ISO 8601 value and call the tool. "
    "Never ask the user to restate a date you can compute."
)

RULE_NEW = (
    "Each user message begins with a [System Data] block giving the current time and "
    'pre-computed dates. Resolve relative dates and times yourself from those values — '
    '"내일", "모레", "다음주 월요일", "오후 3시" are NOT missing information. '
    "Compute the absolute ISO 8601 value and call the tool. "
    "Never ask the user to restate a date you can compute."
)

TAIL_RULES = [
    "Optional parameters are never a reason to ask. Only ask the user when a REQUIRED parameter is genuinely absent.",
    "Never alter numbers, dates, or proper nouns the user gave you — copy them exactly.",
    "If you do not need a tool, simply provide your final response in plain text.",
]


def system(rule, time_in_system):
    parts = list(HEAD)
    if time_in_system:
        parts.append(TIME_BLOCK)
    return "\n".join(parts + TOOL_RULES + [rule] + TAIL_RULES)


# [WHY] `tool_from_function` 은 함수 이름을 그대로 툴 이름으로 쓴다. exp6 에서 좁힌 설명의
# 함수를 `get_schedule_narrowed` 로 둔 탓에 툴 이름이 달라져 두 케이스가 실제 실패가 아닌데도
# FAIL 로 찍혔다. 이름을 되돌려 판정을 정직하게 만든다.
def get_schedule_narrowed(date: str) -> dict:
    """사용자의 캘린더 일정을 조회한다.

    Args:
        date: 조회 범위. 반드시 'today' 또는 'week' 중 하나만 쓴다. 내일·모레·이번주·다음주처럼 오늘이 아닌 날을 물으면 'week' 를 쓴다. 다른 값은 쓰지 않는다.
    """
    raise AssertionError("실행되지 않아야 한다")


get_schedule_narrowed.__name__ = "get_schedule"
get_schedule_narrowed.__qualname__ = "get_schedule"

TOOLS = [K.add_schedule, get_schedule_narrowed, K.add_memory, K.search_wikipedia]

# 조회 질문 — 저장이 아니라 답으로 응해야 한다.
RECALL_CASES = [
    "내 자전거 비밀번호 뭐였지?",
    "내가 뭘 좋아한다고 했지?",
    "아까 저장한 거 뭐였어?",
]

VARIANTS = [
    ("B  새 문구 + 시각을 사용자 턴에 (현재 앱)", RULE_NEW, False, False),
    ("C  새 문구 + 시각을 시스템 지시에 (문구만)", RULE_NEW, True, False),
    ("D  옛 문구 + 시각을 사용자 턴에 (위치만)", RULE_OLD, False, False),
    ("E  현재 앱 + 턴 문맥에 RAG 기억 (앱 실제 조건)", RULE_NEW, False, True),
]


def main():
    lab = K.Lab(backend=BACKEND)
    for label, rule, time_in_system, with_rag in VARIANTS:
        cfg = K.Config(label=label, tools=TOOLS, system=system(rule, time_in_system))
        print(f"\n{'=' * 92}\n[{label}]\n{'=' * 92}")
        clean = 0
        for text in RECALL_CASES:
            if time_in_system:
                prompt = text
            else:
                prompt = TIME_BLOCK + (RAG_BLOCK if with_rag else "") + f"\n\n{text}"
            r = lab.run(prompt, cfg)
            good = not r.tool_calls  # 조회 질문에 툴을 부르면 안 된다
            clean += good
            line = f"  {'OK  ' if good else 'FAIL'} {r.seconds:5.1f}s  {text}"
            if r.tool_calls:
                line += f"\n            → 호출 {r.called} {r.tool_calls[0].get('arguments')}"
            else:
                line += f'\n            → "{r.text[:110].replace(chr(10), " ")}"'
            print(line, flush=True)
        print(f"\n  ── 툴 호출 없이 답한 건수 {clean}/{len(RECALL_CASES)}")
    lab.close()


if __name__ == "__main__":
    main()
