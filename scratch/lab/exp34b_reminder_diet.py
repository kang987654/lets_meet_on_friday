"""B1 M0 2단계: 선언 다이어트 후 오버헤드 재실측 + 툴 선택 기능 스모크.

exp34 실측: add_reminder 선언 +136, 트리거 줄 포함 +182 — 여유 71 초과.
다이어트 원칙: 기존 선언의 둘째 문장들은 시스템 지시 [Tool Usage Guidelines] 의 트리거
규칙과 **중복**이다 — 같은 정보가 두 곳에 있으니 선언 쪽을 깎는다. 신규 선언·트리거도 최소화.
게이트: (다이어트 + 신규) 순증가 <= 71, 그리고 스모크 8 시나리오에서 툴 선택 유지.
"""
import json

import kosmos_lab as K


# --- 다이어트판 기존 5종 (둘째 문장 제거 — 트리거 규칙이 SI 에 있음) ---
def add_schedule(title: str, start_time: str, end_time: str | None, memo: str | None) -> dict:
    """사용자의 캘린더에 일정을 추가한다.

    Args:
        title: 일정 제목. 예: '치과 예약'
        start_time: 시작 시각. ISO 8601 형식. 예: '2026-08-07T15:00:00'
        end_time: 종료 시각. ISO 8601 형식. 모르면 시작 1시간 뒤.
        memo: 메모. 없으면 빈 문자열.
    """
    raise NotImplementedError


def get_schedule(date: str) -> dict:
    """사용자의 캘린더 일정을 조회한다.

    Args:
        date: 조회 범위. 반드시 'today' 또는 'week' 중 하나만 쓴다. 내일·모레·이번주·다음주처럼 오늘이 아닌 날을 물으면 'week' 를 쓴다. 다른 값은 쓰지 않는다.
    """
    raise NotImplementedError


def add_memory(content: str, tags: list[str]) -> dict:
    """사용자에 관한 사실·선호·비밀번호 등을 영구 기억으로 저장한다.

    Args:
        content: 기억할 내용. 사용자가 말한 숫자와 고유명사는 절대 바꾸지 말고 그대로 적는다.
        tags: 분류 태그 목록. 예: ['비밀번호', '자전거']
    """
    raise NotImplementedError


def search_memory(keyword: str) -> dict:
    """사용자가 이전에 저장해 둔 기억(메모)에서 찾는다. 추측해서 답하지 말고 이 도구로 확인한다.

    Args:
        keyword: 찾을 핵심 키워드. 문장이 아니라 명사 위주의 짧은 단어로 쓴다. 예: '자전거 비밀번호', '와이파이', '알레르기'
    """
    raise NotImplementedError


def search_wikipedia(topic: str, lang: str) -> dict:
    """위키백과에서 주제의 요약을 가져온다.

    Args:
        topic: 검색 키워드
        lang: 언어 코드. 'ko' 또는 'en'.
    """
    raise NotImplementedError


# --- 신규 선언 (최소형) ---
def add_reminder(time: str, content: str) -> dict:
    """지정 시각에 알림을 울려 상기시킨다. 캘린더 등록이 아니다.

    Args:
        time: 알림 시각. ISO 8601 형식.
        content: 알림에 표시할 내용
    """
    raise NotImplementedError


DIET_TOOLS = [add_schedule, get_schedule, add_memory, search_memory, search_wikipedia, add_reminder]

TRIGGER_LINE = (
    '- The user asks to be reminded at a specific time — Korean triggers: '
    '"3시에 알려줘", "리마인드": you MUST call `add_reminder`.'
)


def insert_trigger(si: str) -> str:
    lines = si.splitlines()
    last_rule = max(i for i, line in enumerate(lines) if line.startswith("- "))
    lines.insert(last_rule + 1, TRIGGER_LINE)
    return "\n".join(lines)


engine = K.create_engine()
base_si = K.fixture("system_instruction.txt")
diet_si = insert_trigger(base_si)


def count(tools, system):
    with K.create_chat_conversation(engine, tools=tools, system_message=system) as conv:
        conv.send_message("안녕")
        return conv.token_count


base = count(K.ALL_TOOLS, base_si)
diet = count(DIET_TOOLS, diet_si)
print(f"기준(현행 5종): {base}  다이어트 6종+트리거: {diet}  순증가: {diet - base}  게이트(<=71): {'통과' if diet - base <= 71 else '초과'}")

# --- 기능 스모크: 각 시나리오는 새 대화(깨끗한 KV)에서 1턴 ---
SCENARIOS = [
    ("내일 3시에 치과 예약해줘", "add_schedule"),
    ("오늘 일정 뭐 있어?", "get_schedule"),
    ("자전거 비밀번호 1234야 기억해줘", "add_memory"),
    ("자전거 비밀번호 뭐였지?", "search_memory"),
    ("에스파가 뭐야? 검색해줘", "search_wikipedia"),
    ("오후 3시에 약 먹으라고 알려줘", "add_reminder"),
    ("내일 아침 9시에 쓰레기 버리라고 리마인드 해줘", "add_reminder"),
    ("3시에 회의 일정 잡아줘", "add_schedule"),  # 리마인더와의 혼동 감시
]

passed = 0
for utterance, expected in SCENARIOS:
    with K.create_chat_conversation(engine, tools=DIET_TOOLS, system_message=diet_si) as conv:
        res = conv.send_message(K.with_turn_reminder(utterance))
    calls = res.get("tool_calls") or []
    got = calls[0]["function"]["name"] if calls else "(없음)"
    args = json.dumps(calls[0]["function"].get("arguments", {}), ensure_ascii=False) if calls else ""
    ok = got == expected
    passed += ok
    print(f"{'OK ' if ok else 'FAIL'} {utterance!r} -> {got} {args[:120]}")

print(f"\n스모크 {passed}/{len(SCENARIOS)}")
