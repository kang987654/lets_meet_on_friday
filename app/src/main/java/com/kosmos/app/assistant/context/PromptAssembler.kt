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
 * 2. 기본 시스템 인스트럭션(JSON 형식 제약 등) 및 외부 주입 지식 병합
 * 3. [ChatPrompt] 객체로 래핑하여 반환
 */
class PromptAssembler @Inject constructor() {

    fun assemble(context: ContextBuilder.Context, userInput: String): ChatPrompt {
        val systemMessages = context.recentConversations.filter { it.role == com.kosmos.app.domain.model.ChatMessage.Role.SYSTEM }
        val dialogHistory = context.recentConversations.filter { it.role != com.kosmos.app.domain.model.ChatMessage.Role.SYSTEM }
        
        val systemInstruction = buildString {
            appendLine(buildSystemBlock())
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

    private fun buildSystemBlock(): String {
        return """
            [System]
            You are a helpful personal assistant named Local Friday.
            Your task is to respond to the user's input accurately and concisely.
            Always use the specified JSON format to provide your response.
            Do not include any conversational filler outside of the JSON structure.
        """.trimIndent()
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
        // In v0, we only support plain text responses or calendar drafts.
        return """
            [Format Guidelines]
            You MUST output your response as a valid JSON object.
            Do NOT wrap the JSON in Markdown formatting (e.g. ```json).
            
            Supported response formats (choose one based on the user's intent):
            
            1. Standard Text Response:
            {
              "type": "text",
              "text": "Your helpful response here."
            }
            
            2. Calendar Event Draft (if the user wants to schedule something):
            {
              "type": "calendar_draft",
              "title": "Event Title",
              "startTime": "YYYY-MM-DDTHH:MM:SS",
              "description": "Optional description"
            }
        """.trimIndent()
    }

    private fun buildInputBlock(userInput: String): String {
        return """
            [Current Input]
            User: $userInput
            
            Provide your response in JSON:
        """.trimIndent()
    }
}
