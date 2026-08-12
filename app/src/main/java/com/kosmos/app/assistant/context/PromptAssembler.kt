package com.kosmos.app.assistant.context

import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.modelrunner.ChatPrompt
import javax.inject.Inject

/**
 * [PromptAssembler]
 * 모델 추론을 위해 최종적인 프롬프트 문자열 덩어리들을 조립하는 유틸리티 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Context Management)
 * - **Dependencies**: [ContextBuilder.Context], [ChatPrompt]
 *
 * ### Key Flow
 * 1. 컨텍스트에서 System 메시지(RAG 기억)와 일반 사용자/AI History 분리
 * 2. **하루 동안 고정되는** 시스템 지시(역할·스타일·날짜 블록·툴 사용 태도)를 조립
 * 3. **턴마다 달라지는** 검색된 기억은 [ChatPrompt.turnContext] 로 분리
 * 4. [ChatPrompt] 객체로 래핑하여 반환
 *
 * [WHY] 2·3 의 분리가 이 클래스의 핵심 계약이다. 예전에는 **분 단위 시각**과 RAG 기억이 함께
 * 시스템 지시 안에 있어서 지시가 턴마다 달라졌고, 런타임이 그때마다 Conversation 을 파괴하고
 * 툴 선언(~2천 토큰)까지 전부 다시 프리필했다(PC 실측: 2번째 턴 0.5초 → 3.8초).
 *
 * [WHY] 그렇다고 날짜 블록까지 사용자 턴으로 옮기면 **안 된다.** PC 실험에서 `[System Data]`
 * 블록이 사용자 발화 앞에 붙으면 조회 질문이 툴 호출로 샜다 — "내 자전거 비밀번호 뭐였지?"에
 * `add_memory` 를, "내가 뭘 좋아한다고 했지?"에 `get_schedule` 을 호출했다(조회 3케이스 중
 * 1/3 만 정상). 날짜 목록이 날짜·캘린더 툴을 프라이밍하는 것으로 보인다. 같은 조건에서 블록을
 * 시스템 지시에 두면 3/3 정상이었고, 원인이 규칙 문구가 아니라 **블록의 위치**임을 변형 실험
 * (문구만 바꿈 / 위치만 바꿈)으로 분리했다.
 *
 * [WHY] 그래서 날짜 블록은 시스템 지시에 남기되 **분 단위 시계를 뺀다.** 일정 등록에 실제로
 * 필요한 것은 파생 날짜와 요일이고 그것들은 하루에 한 번만 바뀐다. 그 결과 시스템 지시가
 * 하루 동안 고정되어 대화가 재사용된다. 이 배치가 전체 16케이스 **16/16** 으로 최선이었다
 * (분 단위 시각 15/16, 날짜 블록을 턴으로 옮긴 변형 11/14).
 */
class PromptAssembler @Inject constructor() {

    fun assembleWithTools(
        context: ContextBuilder.Context,
        userInput: String,
        availableTools: List<String>,
        systemRole: String
    ): ChatPrompt {
        // [WHY] 현재 턴의 사용자 메시지는 이미 DB에 저장된 뒤 컨텍스트로 로드되므로,
        // history 마지막과 currentInput이 중복되지 않도록 마지막 동일 USER 메시지를 제외한다.
        //
        // [WHY] SYSTEM 역할 메시지는 더 이상 생기지 않는다 — 유일한 생산자였던 RAG 자동 주입을
        // 없앴다(ADR-013). 방어적으로 걸러 두면 "언젠가 올지도 모르는" 경로를 흉내내는 죽은
        // 분기가 되므로 필터도 함께 걷어낸다.
        val dialogHistory = context.recentConversations
            .let { history ->
                val last = history.lastOrNull()
                if (last?.role == ChatMessage.Role.USER && last.content == userInput) {
                    history.dropLast(1)
                } else {
                    history
                }
            }

        val systemInstruction = buildString {
            appendLine(buildSystemBlock(context.responseStyle, systemRole))
            appendLine(buildDateBlock())
            append(buildFormatBlock(availableTools))
        }

        return ChatPrompt(
            sessionId = context.sessionId,
            systemInstruction = systemInstruction,
            history = dialogHistory,
            currentInput = withTurnToolReminder(userInput, availableTools),
            contextBudgetTokens = context.maxTokens
        )
    }

    /**
     * 툴 사용 지침을 **사용자 턴 앞에 한 줄 더** 붙입니다.
     *
     * [WHY] 히스토리가 쌓이면 툴 호출이 멈춘다. 실기기에서 위키가 필요한 발화 4연속 모두
     * `TOOL_CALL` 0건이었고, 모델은 검색하지 않고 "검색하여 가져왔습니다" 로 시작하는 거짓
     * 답변을 냈다(2026-08-12). 지침은 시스템 턴에 한 번만 있고, 히스토리가 길어지면 지침과
     * 사용자 발화 사이에 어시스턴트 산문 수천 토큰이 끼어 **지침이 멀어진다.**
     *
     * [WHY] 원인을 실험으로 좁혔다 (`scratch/lab/`):
     *  - exp18: 문서 형식대로 툴 호출 구조를 히스토리에 복원해도 일정 툴은 **0/3** — 구조 누락이
     *    원인이 아니다
     *  - exp19: 같은 답변을 60자로 자르면 3/3, 120자면 실패 — 어시스턴트 산문의 양이 문제다.
     *    다만 60자로 자르면 대화 기억이 남지 않아 제품 해법이 못 된다
     *  - exp20: 지침을 사용자 턴에 다시 붙이면 **원본 히스토리 그대로** depth 20·40 에서 일정·위키
     *    둘 다 호출된다. 전체 재게시(1,519자)와 이 한 줄(166자)의 결과가 같아 싼 쪽을 쓴다
     *
     * ADR-010 과 같은 종류의 발견이다 — 그때도 `[System Data]` 블록의 **문구가 아니라 위치**가
     * 툴 선택을 갈랐다. 이 모델에게 프롬프트의 거리는 내용만큼 중요하다.
     *
     * [WHY] 시스템 지시의 지침을 옮기지 않고 **더한다.** 위 실험이 검증한 것이 "시스템 지시에
     * 지침이 있는 상태에서 한 줄을 더 붙인 조건" 이므로, 옮기면 검증하지 않은 배치가 된다.
     *
     * [WHY] 히스토리 중복 제거 비교는 원문 `userInput` 으로 한다 — 이 함수의 결과로 비교하면
     * 접두사 때문에 절대 일치하지 않아 마지막 사용자 메시지가 두 번 실린다.
     */
    private fun withTurnToolReminder(userInput: String, availableTools: List<String>): String {
        if (availableTools.isEmpty()) return userInput
        return "$TURN_TOOL_REMINDER\n\n$userInput"
    }

    private companion object {
        // [WHY] 실험(exp20)에서 검증한 문구 그대로다. 문구를 바꾸면 그 검증이 무효가 된다.
        const val TURN_TOOL_REMINDER =
            "[Tool Usage Guidelines] For THIS request, first decide if one of your tools applies. " +
                "If it does, you MUST call the tool — do not answer from memory or merely promise."
    }

    private fun buildSystemBlock(responseStyle: String, systemRole: String): String {
        return buildString {
            appendLine("[System]")
            appendLine("You are a $systemRole")
            appendLine("Always respond in Korean unless the user speaks another language.")
            styleInstruction(responseStyle)?.let { appendLine(it) }
        }.trimEnd()
    }

    /**
     * 응답 스타일 설정을 **모델이 따를 수 있는 지시문**으로 바꿉니다.
     *
     * [WHY] 예전에는 `[Style: CONCISE]` 라벨 한 줄만 넣었다. 모델은 그 대문자 토큰이 무엇을
     * 요구하는지 알 길이 없어 설정이 사실상 아무 효과가 없었다. 설정 화면이 제공하는 세 값은
     * 지시문으로 풀어 주고, 그 밖의 값(사용자가 직접 넣은 문장)은 그대로 전달한다.
     */
    private fun styleInstruction(responseStyle: String): String? = when {
        responseStyle.isBlank() || responseStyle == "DEFAULT" -> null
        responseStyle == "CONCISE" ->
            "Answer in one or two short sentences. Do not restate the question or add a preamble."
        responseStyle == "DETAILED" ->
            "Answer thoroughly: give the reasoning and the relevant context, not just the conclusion."
        else -> "[Style: $responseStyle]"
    }

    /**
     * 오늘 날짜·요일과 파생 날짜를 시스템 지시에 제공합니다.
     *
     * [WHY] 요일과 파생 날짜(내일·모레·다음주 월요일)를 함께 준다. PC 실험에서 모델이 시각만
     * 주면 "다음주 월요일"을 **한 주 틀리게**(8/10 → 8/17) 계산했고, 요일과 파생 날짜를 풀어
     * 주면 정확해졌다. 상대 날짜 해석은 일정 등록의 필수 전제라 계산을 모델에게 맡기지 않는다.
     *
     * [WHY] **분 단위 시계(`HH:mm`)를 넣지 않는다.** 넣으면 시스템 지시가 매 턴 달라져 대화가
     * 재생성되고 턴당 3초 이상을 프리필에만 쓴다(PC 실측). 날짜 단위로 만들면 지시가 하루 동안
     * 고정되어 대화가 재사용되고, 일정 등록 정확도는 전혀 떨어지지 않았다(16/16). 대가는
     * "지금 몇 시야?" 나 "한 시간 뒤" 같은 시계 의존 발화를 다룰 수 없다는 것 — 알림 기능이
     * 없는 현재로서는 지불할 값이 있는 대가이며, 되돌리려면 이 한 줄에 시각을 넣으면 된다
     * (그 순간 프리필 비용이 함께 돌아온다).
     */
    private fun buildDateBlock(): String {
        val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
        val date = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val weekday = today.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.FULL,
            java.util.Locale.ENGLISH
        )
        // [WHY] 한국어에서 주는 월요일에 시작하므로 `next(MONDAY)` 가 곧 "다음주 월요일"이다
        // (오늘이 월요일이면 7일 뒤, 토·일요일이면 이틀·하루 뒤 — 모두 의도와 맞는다).
        val nextMonday = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
        return buildString {
            appendLine("[System Data] Today: ${today.format(date)} ($weekday)")
            append(
                "[System Data] 오늘=${today.format(date)}, 내일=${today.plusDays(1).format(date)}, " +
                    "모레=${today.plusDays(2).format(date)}, 다음주 월요일=${nextMonday.format(date)}"
            )
        }
    }

    /**
     * 툴 사용 **태도**만 지시합니다. 툴 목록과 호출 형식은 여기 없습니다.
     *
     * [WHY] 이전에는 `<tool_call>{"name":...}</tool_call>` 형식과 툴 스키마를 이 블록에 글로
     * 적었다. 그 규약은 Gemma 의 채팅 템플릿에 없어서 실기기에서 모델이 **한 번도** 따르지
     * 않았다 — 평문으로 "저는 영구적으로 저장하지 않습니다"라고 답했다. 이제 선언은
     * `ConversationConfig.tools` 가 모델의 정식 함수호출 템플릿으로 전달한다 (ADR-008).
     */
    // [WHY] trimIndent 템플릿에 여러 줄 변수를 보간하면 보간된 줄만 들여쓰기가 어긋난다
    // (예전 $toolsDesc 포맷 버그와 동일 패턴). 조건부 여러 줄 블록이므로 buildString 으로 만든다.
    private fun buildFormatBlock(availableTools: List<String>?): String = buildString {
        appendLine("[Tool Usage Guidelines]")
        if (availableTools.isNullOrEmpty()) {
            appendLine("You have no tools available in this turn.")
        } else {
            // [WHY] 런타임이 모델에게 알리는 이름은 snake_case 다 — 지시도 그 이름으로 해야
            // 선언과 이어진다. gallery 의 agent chat 프롬프트도 백틱으로 툴 이름을 직접
            // 지목하며 "MUST call" 수준으로 강제한다 — 일반적 권고("tools available")만으로는
            // 4B 모델이 실기기에서 호출 대신 말로만 약속했다 (0.8.3 확인).
            appendLine(
                "For EVERY user request, first decide if one of your tools applies. " +
                    "If it does, you MUST call the tool. Do NOT merely promise or pretend — " +
                    "promising without calling is a failure."
            )
            // [WHY] 0.8.5 실기기에서 "add_memory 툴을 사용해서 저장해줘"(툴 이름 직접 지목)는
            // 호출됐지만 "기억해줘"는 호출되지 않았다 — 영어 규칙과 한국어 표현 사이에 다리가
            // 없었다. 실제 한국어 트리거 표현을 규칙 안에 예시로 박아 그 간극을 메운다.
            if (availableTools.contains("AddMemory")) {
                appendLine("- The user asks you to remember something, or shares a fact/preference/password to keep — Korean triggers: \"기억해\", \"기억해줘\", \"저장해줘\", \"메모해줘\", \"잊지 마\": you MUST call `add_memory`.")
            }
            // [WHY] 회상은 저장과 반대 방향인데 트리거 표현이 비슷해서 모델이 `add_memory` 를
            // 잘못 부르는 것이 실측됐다("내 자전거 비밀번호 뭐였지?" → add_memory). 조회 전용
            // 툴을 주고 규칙에서 방향을 못 박는다.
            if (availableTools.contains("SearchMemory")) {
                appendLine("- The user asks about something they told you earlier — Korean triggers: \"뭐였지\", \"뭐라고 했지\", \"내가 알려준\", \"기억나\", \"저장한 거\": you MUST call `search_memory` with a short noun keyword. This is a LOOKUP, never call `add_memory` for it.")
            }
            if (availableTools.contains("AddSchedule")) {
                appendLine("- The user asks to add an appointment, reservation, or event — Korean triggers: \"예약\", \"약속\", \"일정 잡아줘\", \"일정 추가\", \"~하기로 했어\": you MUST call `add_schedule`.")
            }
            if (availableTools.contains("GetSchedule")) {
                appendLine("- The user asks what is on their calendar — Korean triggers: \"오늘 일정\", \"내일 일정\", \"스케줄 뭐 있어\": you MUST call `get_schedule`.")
            }
            if (availableTools.contains("SearchWikipedia")) {
                appendLine("- The user asks a factual question you are not sure about — Korean triggers: \"검색해줘\", \"찾아봐\", \"~가 뭐야?\": call `search_wikipedia`.")
            }
        }
        // [WHY] "above" 는 같은 시스템 지시 안의 [System Data] 날짜 블록을 가리킨다. 그 블록의
        // 위치를 옮기면 이 문구도 함께 옮겨야 한다 — 가리키는 곳이 틀리면 규칙이 무력해진다.
        appendLine(
            "Resolve relative dates and times yourself from the [System Data] values above — " +
                "\"내일\", \"모레\", \"다음주 월요일\", \"오후 3시\" are NOT missing information. " +
                "Compute the absolute ISO 8601 value and call the tool. " +
                "Never ask the user to restate a date you can compute."
        )
        // [WHY] 이전 문구는 "If you lack mandatory information … DO NOT guess. Ask the user
        // first." 였다. PC 실험에서 이 한 줄이 **일정 등록을 막는 주범**이었다 — 모델이 상대
        // 날짜를 '없는 필수 정보'로 판단해 호출 대신 되물었고, 선택 파라미터인 end_time 까지
        // 필수로 착각했다. 일정 4케이스 중 3케이스 실패 → 위·아래 두 줄로 교체하니 4/4 성공.
        appendLine(
            "Optional parameters are never a reason to ask. Only ask the user when a REQUIRED " +
                "parameter is genuinely absent."
        )
        appendLine("Never alter numbers, dates, or proper nouns the user gave you — copy them exactly.")
        append("If you do not need a tool, simply provide your final response in plain text.")
    }
}
