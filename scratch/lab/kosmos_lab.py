"""앱의 실경로와 동일한 설정으로 Gemma 4 E4B 를 PC 에서 돌리는 최소 하네스 (0.15.0 재구축).

[WHY] 실기기 테스트 횟수를 줄이기 위한 도구다. 설정이 하나라도 앱과 다르면 실험은
앱과 다른 것을 측정한다. 그래서 값은 전부 앱 코드/픽스처에서 온다:
- 시스템 지시·턴 지침·툴 목록: scratch/lab/fixtures/ (PromptFixtureExportTest 가 내보냄
  — 손으로 베낀 사본은 낡는다, ADR-016 부수 결론)
- 엔진 설정: GemmaModelRunner.buildEngineConfig 와 동일 (max_num_tokens=4096,
  max_num_images=1). 백엔드만 CPU — PC 에는 GPU 델리게이트가 없다.
- 대화 설정: getOrCreateConversation 와 동일 (greedy topK=1, ThinkingConfig off,
  automatic_tool_calling=False, 툴 선언 턴에만 제약 디코딩 on).

주의: 파이썬 런타임은 KV 용량 초과를 오류로 거부한다(exp17). 실기기 AAR 은 조용히
진행하므로, 이 하네스의 통과는 **용량 이내에서만** 유효하다.
"""

import pathlib

import litert_lm as llm

LAB = pathlib.Path(__file__).parent
FIXTURES = LAB / "fixtures"
MODEL_PATH = str(LAB.parent / "gemma-4-E4B-it.litertlm")

ENGINE_MAX_TOKENS = 4096  # Constants.ENGINE_MAX_TOKENS
MAX_IMAGES_PER_TURN = 1  # Constants.MAX_IMAGES_PER_TURN


def fixture(name: str) -> str:
    return (FIXTURES / name).read_text(encoding="utf-8")


def with_turn_reminder(user_input: str) -> str:
    """PromptAssembler.withTurnToolReminder 와 동일 — 툴이 선언된 턴에만 쓴다."""
    return f"{fixture('turn_reminder.txt')}\n\n{user_input}"


# --- 툴 선언: KosmosToolDeclarations 와 동일한 snake_case 이름·파라미터 ---
# 본문은 실행되지 않는다(automatic_tool_calling=False). 설명은 @Tool description 원문.


def add_schedule(title: str, start_time: str, end_time: str | None, memo: str | None) -> dict:
    """사용자의 캘린더에 일정을 추가한다.

    Args:
        title: 일정 제목. 예: '치과 예약'
        start_time: 시작 시각. ISO 8601 형식. 예: '2026-08-07T15:00:00'
        end_time: 종료 시각. ISO 8601 형식. 모르면 시작 1시간 뒤.
        memo: 메모. 없으면 빈 문자열.
    """
    raise NotImplementedError("툴 실행은 하네스 밖에서 주입한다")


def get_schedule(date: str) -> dict:
    """사용자의 캘린더 일정을 조회한다.

    Args:
        date: 조회 범위. 반드시 'today' 또는 'week' 중 하나만 쓴다. 내일·모레·이번주·다음주처럼 오늘이 아닌 날을 물으면 'week' 를 쓴다. 다른 값은 쓰지 않는다.
    """
    raise NotImplementedError("툴 실행은 하네스 밖에서 주입한다")


def add_memory(content: str, tags: list[str]) -> dict:
    """사용자에 관한 사실·선호·비밀번호 등을 영구 기억으로 저장한다.

    Args:
        content: 기억할 내용. 사용자가 말한 숫자와 고유명사는 절대 바꾸지 말고 그대로 적는다.
        tags: 분류 태그 목록. 예: ['비밀번호', '자전거']
    """
    raise NotImplementedError("툴 실행은 하네스 밖에서 주입한다")


def search_memory(keyword: str) -> dict:
    """사용자가 이전에 저장해 둔 기억(메모)에서 찾는다. 추측해서 답하지 말고 이 도구로 확인한다.

    Args:
        keyword: 찾을 핵심 키워드. 문장이 아니라 명사 위주의 짧은 단어로 쓴다. 예: '자전거 비밀번호', '와이파이', '알레르기'
    """
    raise NotImplementedError("툴 실행은 하네스 밖에서 주입한다")


def search_wikipedia(topic: str, lang: str) -> dict:
    """위키백과에서 주제의 요약을 가져온다.

    Args:
        topic: 검색 키워드
        lang: 언어 코드. 'ko' 또는 'en'.
    """
    raise NotImplementedError("툴 실행은 하네스 밖에서 주입한다")


def add_reminder(time: str, content: str) -> dict:
    """지정 시각에 알림을 울려 상기시킨다. 캘린더 등록이 아니다.

    Args:
        time: 알림 시각. ISO 8601 형식.
        content: 알림에 표시할 내용
    """
    raise NotImplementedError("툴 실행은 하네스 밖에서 주입한다")


# 0.21.0 다이어트: 위 선언들의 둘째 문장(시스템 지시 트리거 규칙과 중복)을 앱과 함께 제거 —
# 근거·실측은 exp34b_reminder_diet.py (순증 52토큰, 스모크 8/8).
ALL_TOOLS = [add_schedule, get_schedule, add_memory, search_memory, search_wikipedia, add_reminder]


def create_engine(backend=None) -> "llm.Engine":
    """기본은 CPU. exp28(백엔드 A/B)만 GPU 를 명시한다 — 앱의 실기기 기본은 GPU 다."""
    return llm.Engine(
        MODEL_PATH,
        backend=backend if backend is not None else llm.Backend.CPU,
        max_num_tokens=ENGINE_MAX_TOKENS,
        max_num_images=MAX_IMAGES_PER_TURN,
    )


def few_shot_messages():
    """GemmaModelRunner.fewShotToolExample 원문 미러 — 채팅 대화 서두에 주입되는 툴 왕복 시범.

    [WHY] 0.15.0 재구축 때 누락됐던 것을 0.23.0 에서 복원 — 없으면 하네스가 앱보다 ~104토큰
    과소 측정하고, few-shot 제거 실험(exp35)의 대조군을 만들 수 없다.
    """
    return [
        llm.Message.user("내 자물쇠 비밀번호는 8282야, 기억해줘"),
        llm.Message.model(
            llm.Contents([llm.Content.Text("")]),
            [llm.ToolCall("add_memory", {"content": "자물쇠 비밀번호는 8282", "tags": ["비밀번호", "자물쇠"]})],
        ),
        llm.Message.tool(llm.Contents([llm.Content.ToolResponse("add_memory", '{"status":"success"}')])),
        llm.Message.model(llm.Contents([llm.Content.Text("자물쇠 비밀번호 8282를 기억해 두었습니다.")])),
    ]


def create_chat_conversation(engine, history=None, tools=ALL_TOOLS, system_message=None, few_shot=False):
    """앱 채팅 대화와 동일 설정. history 는 [{'role': 'user'|'model', 'content': str}].

    few_shot 기본 False — 앱은 0.23.0 에서 few-shot 을 제거했다(exp35 판정, 104토큰을
    프로필 상시 주입 재원으로 회수). True 는 과거 조건 재현·A/B 용으로만 남긴다.
    """
    inject_few_shot = few_shot and tools and (add_memory in tools)
    initial = (few_shot_messages() if inject_few_shot else []) + list(history or [])
    return engine.create_conversation(
        system_message=system_message if system_message is not None else fixture("system_instruction.txt"),
        messages=initial or None,
        tools=tools,
        automatic_tool_calling=False,
        thinking_config=llm.ThinkingConfig(enable_thinking=False),
        sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
        constrained_decoding_config=llm.ConstrainedDecodingConfig(enable=bool(tools)),
    )


def tool_response_message(name: str, result_json: str):
    """GemmaModelRunner.buildMessage 의 toolResponse 분기와 동일 — TOOL 역할 메시지."""
    return llm.Message.tool(llm.Contents([llm.Content.ToolResponse(name, result_json)]))
