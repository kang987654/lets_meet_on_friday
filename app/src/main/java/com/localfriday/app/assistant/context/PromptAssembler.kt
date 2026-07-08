package com.localfriday.app.assistant.context

import com.localfriday.app.domain.modelrunner.ChatPrompt
import javax.inject.Inject

class PromptAssembler @Inject constructor() {

    fun assemble(context: ContextBuilder.Context, userInput: String): ChatPrompt {
        val systemMessages = context.recentConversations.filter { it.role == com.localfriday.app.domain.model.ChatMessage.Role.SYSTEM }
        val dialogHistory = context.recentConversations.filter { it.role != com.localfriday.app.domain.model.ChatMessage.Role.SYSTEM }
        
        val systemInstruction = buildString {
            appendLine(buildSystemBlock())
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
              "endTime": "YYYY-MM-DDTHH:MM:SS",
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
