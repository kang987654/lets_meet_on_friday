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
        val dialogHistory = context.recentConversations.filter { it.role != com.kosmos.app.domain.model.ChatMessage.Role.SYSTEM }
        
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

    private fun buildTimeBlock(): String {
        val now = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return "[System Data] Current Time: ${now.format(formatter)}"
    }

    private fun buildFormatBlock(availableTools: List<String>? = null): String {
        val toolsDesc = buildString {
            if (availableTools == null || availableTools.contains("AddSchedule")) {
                appendLine("""
                    - "AddSchedule": Adds an event to the calendar.
                      Args: {"title": "string", "startTime": "ISO 8601", "endTime": "ISO 8601 (Optional)", "description": "string (Optional)"}
                """.trimIndent())
            }
            if (availableTools == null || availableTools.contains("GetSchedule")) {
                appendLine("""
                    - "GetSchedule": Retrieves today's or this week's schedule.
                      Args: {"date": "string ('today' or 'week')"}
                """.trimIndent())
            }
            if (availableTools == null || availableTools.contains("AddMemory")) {
                appendLine("""
                    - "AddMemory": Saves permanent knowledge, facts, or preferences about the user.
                      Args: {"content": "string (the fact to remember)", "tags": "string array (e.g. ['food', 'preference'])"}
                """.trimIndent())
            }
            if (availableTools == null || availableTools.contains("SearchWikipedia")) {
                appendLine("""
                    - "SearchWikipedia": Query summary and infobox from Wikipedia for a given topic.
                      Args: {"topic": "string (search keyword)", "lang": "string (e.g. 'ko' or 'en')"}
                """.trimIndent())
            }
        }.trimEnd()

        return """
            [Tool Usage Guidelines]
            You have access to several tools. When you need to perform an action, output a tool call using the following XML format:
            
            <tool_call>
            {"name": "ToolName", "args": {"key": "value"}}
            </tool_call>
            
            Available Tools:
$toolsDesc
            
            If you lack mandatory information to use a tool, DO NOT guess. Ask the user for clarification first.
            If you do not need a tool, simply provide your final response in plain text.
        """.trimIndent()
    }

    private fun buildInputBlock(userInput: String): String {
        return """
            [Current Input]
            $userInput
        """.trimIndent()
    }
}
