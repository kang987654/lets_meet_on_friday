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

    private fun buildSystemBlock(responseStyle: String): String {
        return buildString {
            appendLine("[System]")
            appendLine("You are a helpful personal assistant named Local Friday.")
            appendLine("Your task is to respond to the user's input accurately and concisely.")
            appendLine("Do not include any conversational filler.")
            if (responseStyle.isNotBlank() && responseStyle != "DEFAULT") {
                appendLine("User's preferred response style: $responseStyle. You MUST strictly follow this style when answering.")
            }
        }.trimEnd()
    }

    private fun buildTimeBlock(): String {
        val now = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (E)")
        val formattedTime = now.format(formatter)
        return """
            [System Clock]
            Current System Time: $formattedTime
            You have access to the system clock. Today's date/time is $formattedTime. When the user refers to relative dates/times like 'tomorrow', 'next week', 'Friday at 3pm', use this system clock to calculate the exact dates and times.
        """.trimIndent()
    }

    private fun buildFormatBlock(): String {
        return """
            [Tool Usage Guidelines]
            You have access to several tools. When you need to perform an action (e.g. schedule an event, search the web), output a tool call using the following XML format:
            
            <tool_call>
            {"name": "ToolName", "args": {"key": "value"}}
            </tool_call>
            
            Available Tools:
            - "AddSchedule": Adds an event to the calendar. Args: title (String), startTime (String, ISO format), endTime (String, ISO format), description (String, Optional).
            - "GetSchedule": Retrieves today's or this week's schedule. Args: date (String, e.g., "today" or "week").
            - "SearchWeb": Searches the web for information. Args: query (String).
            
            If you do not need a tool, simply provide your final response in plain text.
        """.trimIndent()
    }

    private fun buildInputBlock(userInput: String): String {
        return """
            [Current Input]
            User: $userInput
            
            Assistant:
        """.trimIndent()
    }
}
