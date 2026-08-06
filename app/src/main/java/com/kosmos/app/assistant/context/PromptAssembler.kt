package com.kosmos.app.assistant.context

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
 * 1. 컨텍스트에서 System 메시지와 일반 사용자/AI History 분리
 * 2. 기본 시스템 인스트럭션 및 외부 주입 지식 병합
 * 3. [ChatPrompt] 객체로 래핑하여 반환
 */
class PromptAssembler @Inject constructor() {

    fun assemble(context: ContextBuilder.Context, userInput: String): ChatPrompt {
        val systemMessages = context.recentConversations.filter { it.role == com.kosmos.app.domain.model.ChatMessage.Role.SYSTEM }
        val dialogHistory = context.recentConversations.filter { it.role != com.kosmos.app.domain.model.ChatMessage.Role.SYSTEM }
        
        val systemInstruction = buildString {
            appendLine(buildSystemBlock(context.responseStyle))
            appendLine(buildTimeBlock())
            appendLine(buildFormatBlock())
            if (systemMessages.isNotEmpty()) {
                appendLine("\n[Context / Knowledge]")
                systemMessages.forEach { msg ->
                    appendLine(msg.content)
                }
            }
        }

        return ChatPrompt(
            sessionId = context.sessionId,
            systemInstruction = systemInstruction,
            history = dialogHistory,
            currentInput = buildInputBlock(userInput)
        )
    }

    fun assembleWithTools(context: ContextBuilder.Context, userInput: String, availableTools: List<String>, systemRole: String): ChatPrompt {
        val systemMessages = context.recentConversations.filter { it.role == com.kosmos.app.domain.model.ChatMessage.Role.SYSTEM }
        // [WHY] 현재 턴의 사용자 메시지는 이미 DB에 저장된 뒤 컨텍스트로 로드되므로,
        // history 마지막과 currentInput이 중복되지 않도록 마지막 동일 USER 메시지를 제외한다.
        val dialogHistory = context.recentConversations
            .filter { it.role != com.kosmos.app.domain.model.ChatMessage.Role.SYSTEM }
            .let { history ->
                val last = history.lastOrNull()
                if (last?.role == com.kosmos.app.domain.model.ChatMessage.Role.USER && last.content == userInput) {
                    history.dropLast(1)
                } else {
                    history
                }
            }

        val systemInstruction = buildString {
            appendLine(buildSystemBlock(context.responseStyle, systemRole))
            appendLine(buildTimeBlock())
            appendLine(buildFormatBlock(availableTools))
            if (systemMessages.isNotEmpty()) {
                appendLine("\n[Context / Knowledge]")
                systemMessages.forEach { msg ->
                    appendLine(msg.content)
                }
            }
        }

        return ChatPrompt(
            sessionId = context.sessionId,
            systemInstruction = systemInstruction,
            history = dialogHistory,
            currentInput = buildInputBlock(userInput)
        )
    }

    private fun buildSystemBlock(responseStyle: String, systemRole: String = "personal assistant named Kosmos."): String {
        return buildString {
            appendLine("[System]")
            appendLine("You are a $systemRole")
            appendLine("Always respond in Korean unless the user speaks another language.")
            if (responseStyle.isNotBlank() && responseStyle != "DEFAULT") {
                appendLine("[Style: $responseStyle]")
            }
        }.trimEnd()
    }

    /**
     * [WHY] 초 단위였던 것을 분 단위로 내렸다. 시스템 지시가 매 턴 달라지면
     * `GemmaModelRunner.getOrCreateConversation` 의 재사용 조건이 항상 거짓이 되어
     * Conversation 을 매번 파괴·재생성(전체 프리필)했다 — 그 판정이 사실상 죽은 코드였다.
     *
     * [WHY] 요일과 파생 날짜(내일·모레·다음주 월요일)를 함께 준다. PC 실험에서 모델이 시각만
     * 주면 "다음주 월요일"을 **한 주 틀리게**(8/10 → 8/17) 계산했고, 요일과 파생 날짜를 풀어
     * 주면 정확해졌다. 상대 날짜 해석은 일정 등록의 필수 전제라 계산을 모델에게 맡기지 않는다.
     */
    private fun buildTimeBlock(): String {
        val now = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault())
        val dateTime = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val date = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val weekday = now.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.FULL,
            java.util.Locale.ENGLISH
        )
        // [WHY] 한국어에서 주는 월요일에 시작하므로 `next(MONDAY)` 가 곧 "다음주 월요일"이다
        // (오늘이 월요일이면 7일 뒤, 토·일요일이면 이틀·하루 뒤 — 모두 의도와 맞는다).
        val nextMonday = now.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
        return buildString {
            appendLine("[System Data] Current Time: ${now.format(dateTime)} ($weekday)")
            append(
                "[System Data] 오늘=${now.format(date)}, 내일=${now.plusDays(1).format(date)}, " +
                    "모레=${now.plusDays(2).format(date)}, 다음주 월요일=${nextMonday.format(date)}"
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
     *
     * [WHY] 부수 효과로 `$'$'toolsDesc` 가 열 0 에 있어 `trimIndent()` 가 블록 전체의 들여쓰기를
     * 못 벗기던 포맷 버그도 사라졌다.
     */
    // [WHY] trimIndent 템플릿에 여러 줄 변수를 보간하면 보간된 줄만 들여쓰기가 어긋난다
    // (예전 $toolsDesc 포맷 버그와 동일 패턴). 조건부 여러 줄 블록이므로 buildString 으로 만든다.
    private fun buildFormatBlock(availableTools: List<String>? = null): String = buildString {
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
        // [WHY] 이전 문구는 "If you lack mandatory information … DO NOT guess. Ask the user
        // first." 였다. PC 실험에서 이 한 줄이 **일정 등록을 막는 주범**이었다 — 모델이 상대
        // 날짜("내일", "모레", "다음주 월요일")를 '없는 필수 정보'로 판단해 호출 대신 되물었고
        // (자기가 날짜를 계산해 놓고도 되물었다), 선택 파라미터인 end_time 까지 필수로
        // 착각했다. 일정 4케이스 중 3케이스 실패 → 아래 두 줄로 교체하니 4/4 성공.
        appendLine(
            "Resolve relative dates and times yourself from the [System Data] values above — " +
                "\"내일\", \"모레\", \"다음주 월요일\", \"오후 3시\" are NOT missing information. " +
                "Compute the absolute ISO 8601 value and call the tool. " +
                "Never ask the user to restate a date you can compute."
        )
        appendLine(
            "Optional parameters are never a reason to ask. Only ask the user when a REQUIRED " +
                "parameter is genuinely absent."
        )
        appendLine("Never alter numbers, dates, or proper nouns the user gave you — copy them exactly.")
        append("If you do not need a tool, simply provide your final response in plain text.")
    }

    // [WHY] "[Current Input]" 라벨은 자체 XML 규약 시절의 잔재다. 네이티브 함수호출 경로에서
    // 사용자 턴은 템플릿이 역할을 표시하므로 원문 그대로 보낸다 — gallery 도 raw 입력을 보낸다.
    private fun buildInputBlock(userInput: String): String = userInput
}
