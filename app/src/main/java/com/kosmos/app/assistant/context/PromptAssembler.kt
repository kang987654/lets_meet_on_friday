package com.kosmos.app.assistant.context

import com.kosmos.app.domain.tool.ToolNames
import com.kosmos.app.core.common.ResponseStyle
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
 * 1. 히스토리에서 직전 동일 사용자 발화를 제외(현재 턴과 중복 방지)
 * 2. **하루 동안 고정되는** 시스템 지시(역할·스타일·날짜 블록·툴 사용 태도)를 조립
 * 3. 사용자 턴 앞에 툴 지침 한 줄을 덧붙임 ([withTurnToolReminder])
 * 4. [ChatPrompt] 객체로 래핑하여 반환
 *
 * [WHY] 시스템 지시를 **하루 동안 고정**하는 것이 이 클래스의 핵심 계약이다 — 지시가 바뀌면 런타임이 대화를 버리고
 * 툴 선언까지 다시 프리필한다. 그래서 날짜 블록은 시스템 지시에 두되(사용자 턴으로 옮기면 조회 질문이 날짜·캘린더 툴로
 * 샌다) 분 단위 시계는 넣지 않는다(ADR-010).
 *
 * [WHY] 이 클래스의 영어 문구는 전부 실험실 실측 원문이다 — 바꾸려면 실측이 먼저다(AGENTS §2-⑤). 실측 오버헤드의
 * 진실은 `TokenBudgetInvariantTest.MEASURED_OVERHEAD`.
 */
class PromptAssembler @Inject constructor() {

    fun assembleWithTools(
        context: ContextBuilder.Context,
        userInput: String,
        availableTools: List<String>,
        systemRole: String,
        // [WHY] 날짜는 인자로 받는다(AGENTS §2-④) — 벽시계를 안에서 읽으면 테스트가 실행 날짜에 묶인다.
        today: java.time.LocalDate = java.time.LocalDate.now(java.time.ZoneId.systemDefault()),
        // [WHY] 문서가 첨부된 턴은 턴 리마인더를 문서용으로 바꾼다 — 표준 리마인더가 문서 질문을 툴로 끌어간다(ADR-028).
        documentAttached: Boolean = false
    ): ChatPrompt {
        // [WHY] 현재 턴의 사용자 메시지는 이미 DB에 저장된 뒤 컨텍스트로 로드되므로, 마지막 동일 USER 메시지를
        // 빼야 두 번 실리지 않는다. 비교는 리마인더를 붙이기 전 원문으로 한다(붙인 뒤면 절대 일치하지 않는다).
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
            // [WHY] 프로필(C′1)은 [System] 과 [System Data] 사이 — 날짜 블록 위치(ADR-010)와 형식 블록의 "above"
            // 포인터를 건드리지 않는 유일한 자리다. 렌더가 바이트-안정이라 프로필이 그대로면 시스템 지시도 그대로다.
            if (context.profileText.isNotEmpty()) {
                appendLine(context.profileText)
            }
            appendLine(buildDateBlock(today))
            append(buildFormatBlock(availableTools))
        }

        return ChatPrompt(
            sessionId = context.sessionId,
            systemInstruction = systemInstruction,
            history = dialogHistory,
            currentInput = withTurnToolReminder(userInput, availableTools, documentAttached),
            contextBudgetTokens = context.maxTokens
        )
    }

    /**
     * 툴 사용 지침을 **사용자 턴 앞에 한 줄 더** 붙입니다.
     *
     * [WHY] 히스토리가 길어지면 시스템 지시의 지침이 어시스턴트 산문 뒤로 멀어져 툴 호출이 멈추고, 모델은 "검색해
     * 가져왔습니다" 같은 거짓 답을 낸다. 지침을 턴마다 다시 붙이면 긴 히스토리에서도 호출된다(exp20, ADR-017).
     * 시스템 지시의 지침은 옮기지 않고 **더한다** — 검증된 배치가 그것이다.
     */
    private fun withTurnToolReminder(userInput: String, availableTools: List<String>, documentAttached: Boolean = false): String {
        if (availableTools.isEmpty()) return userInput
        // [WHY] 툴을 끄지 않고 리마인더만 바꾼다 — 툴을 끄면 "일정 추가해줘"에 거짓 완료를 냈고, 시스템 지시가 그대로라
        // 대화 KV 재사용도 유지된다(ADR-028).
        val reminder = if (documentAttached) DOC_TURN_REMINDER else TURN_TOOL_REMINDER
        return "$reminder\n\n$userInput"
    }

    private companion object {
        // exp20 실측 원문.
        const val TURN_TOOL_REMINDER =
            "[Tool Usage Guidelines] For THIS request, first decide if one of your tools applies. " +
                "If it does, you MUST call the tool — do not answer from memory or merely promise."

        // exp46b 실측 원문(조건 D).
        const val DOC_TURN_REMINDER =
            "[Attached Document] The user attached a document above. Answer questions about it from the document itself, " +
                "not with tools. Call a tool only if the user explicitly asks to save, schedule, or remind something."
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
     * [WHY] `[Style: CONCISE]` 같은 라벨만으로는 모델이 무엇을 요구하는지 몰라 효과가 없다. 설정 화면의 세 값은
     * 지시문으로 풀고, 그 밖의 값(사용자가 직접 넣은 문장)은 그대로 전달한다.
     */
    private fun styleInstruction(responseStyle: String): String? = when {
        responseStyle.isBlank() || responseStyle == ResponseStyle.DEFAULT -> null
        responseStyle == ResponseStyle.CONCISE ->
            "Answer in one or two short sentences. Do not restate the question or add a preamble."
        responseStyle == ResponseStyle.DETAILED ->
            "Answer thoroughly: give the reasoning and the relevant context, not just the conclusion."
        else -> "[Style: $responseStyle]"
    }

    /**
     * 오늘 날짜·요일과 파생 날짜를 시스템 지시에 제공합니다.
     *
     * [WHY] 파생 날짜를 계산해 준다 — 모델에게 맡기면 "다음주 월요일"을 한 주 틀리게 계산했다. 상대 날짜 해석은
     * 일정 등록의 전제다.
     *
     * [WHY] **분 단위 시계를 넣지 않는다** — 넣으면 시스템 지시가 매 턴 바뀌어 턴마다 전체 재프리필이다(ADR-010).
     * 대가로 "지금 몇 시야"·"한 시간 뒤" 같은 시계 의존 발화는 다루지 못한다(리마인더도 절대 시각만).
     */
    private fun buildDateBlock(today: java.time.LocalDate): String {
        val date = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val weekday = today.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.FULL,
            java.util.Locale.ENGLISH
        )
        // [WHY] 한국어에서 주는 월요일에 시작하므로 `next(MONDAY)` 가 곧 "다음주 월요일"이다
        // (오늘이 월요일이면 7일 뒤, 토·일요일이면 이틀·하루 뒤 — 모두 의도와 맞는다).
        val nextMonday = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
        return buildString {
            append(
                "[System Data] 오늘=${today.format(date)} ($weekday), 내일=${today.plusDays(1).format(date)}, " +
                    "모레=${today.plusDays(2).format(date)}, 다음주 월요일=${nextMonday.format(date)}"
            )
        }
    }

    /**
     * 툴 사용 **태도**만 지시합니다. 툴 목록과 호출 형식은 여기 없습니다.
     *
     * [WHY] 툴 선언은 `ConversationConfig.tools` 가 모델의 정식 함수호출 템플릿으로 전달한다 — 글로 적은 호출 규약은
     * 모델이 따르지 않았다(ADR-008).
     */
    private fun buildFormatBlock(availableTools: List<String>?): String = buildString {
        appendLine("[Tool Usage Guidelines]")
        if (availableTools.isNullOrEmpty()) {
            appendLine("You have no tools available in this turn.")
        } else {
            // [WHY] 툴 이름은 선언과 같은 snake_case 를 백틱으로 지목하고 "MUST call" 로 강제한다 — 일반적 권고만으로는
            // 4B 모델이 호출 대신 말로만 약속했다(0.8.3). 이 약속 금지 문장은 빼면 회상 호출이 줄었다(exp45).
            appendLine(
                "For EVERY user request, first decide if one of your tools applies. " +
                    "If it does, you MUST call the tool. Do NOT merely promise or pretend — " +
                    "promising without calling is a failure."
            )
            // [WHY] 규칙마다 실제 한국어 트리거 표현을 박는다 — 영어 규칙만으로는 "기억해줘"가 호출로 이어지지 않았다(0.8.5).
            if (availableTools.contains(ToolNames.ADD_MEMORY)) {
                appendLine("- The user asks you to remember something, or shares a fact/preference/password to keep: \"기억해\", \"기억해줘\", \"저장해줘\", \"메모해줘\", \"잊지 마\": you MUST call `add_memory`.")
            }
            // [WHY] 회상은 저장과 트리거 표현이 비슷해 `add_memory` 로 새기 쉽다 — 조회 방향을 규칙에서 못 박는다.
            if (availableTools.contains(ToolNames.SEARCH_MEMORY)) {
                appendLine("- The user asks about something they told you earlier: \"뭐였지\", \"뭐라고 했지\", \"내가 알려준\", \"기억나\", \"저장한 거\": you MUST call `search_memory` with a short noun keyword. This is a LOOKUP, never call `add_memory` for it.")
            }
            if (availableTools.contains(ToolNames.ADD_SCHEDULE)) {
                appendLine("- The user asks to add an appointment, reservation, or event: \"예약\", \"약속\", \"일정 잡아줘\", \"일정 추가\", \"~하기로 했어\": you MUST call `add_schedule`.")
            }
            if (availableTools.contains(ToolNames.GET_SCHEDULE)) {
                appendLine("- The user asks what is on their calendar: \"오늘 일정\", \"내일 일정\", \"스케줄 뭐 있어\": you MUST call `get_schedule`.")
            }
            if (availableTools.contains(ToolNames.SEARCH_WIKIPEDIA)) {
                appendLine("- The user asks a factual question you are not sure about: \"검색해줘\", \"찾아봐\", \"~가 뭐야?\": call `search_wikipedia`.")
            }
            if (availableTools.contains(ToolNames.ADD_REMINDER)) {
                appendLine("- The user asks to be reminded at a specific time: \"3시에 알려줘\", \"리마인드\": you MUST call `add_reminder`.")
            }
        }
        // [WHY] "above" 는 [System Data] 날짜 블록을 가리킨다 — 블록을 옮기면 이 문구도 옮겨야 한다. 마지막 문장은 직전
        // 턴의 "내일"에 날짜 없는 시각이 끌려가는 것을 막는다(exp39c).
        appendLine(
            "Resolve relative dates and times yourself from the [System Data] values above — " +
                "\"내일\", \"모레\", \"다음주 월요일\", \"오후 3시\" are NOT missing information. " +
                "Compute the absolute ISO 8601 value and call the tool. " +
                "Never ask the user to restate a date you can compute. " +
                "A time with no date word means today."
        )
        // [WHY] "모르면 묻는다"류 문구는 상대 날짜·선택 파라미터(end_time)까지 '없는 정보'로 보게 해 일정 등록을 막았다 —
        // 물어도 되는 경우를 필수 파라미터로 좁힌다.
        appendLine(
            "Optional parameters are never a reason to ask. Only ask the user when a REQUIRED " +
                "parameter is genuinely absent."
        )
        append("Never alter numbers, dates, or proper nouns the user gave you — copy them exactly.")
    }
}
