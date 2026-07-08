package com.localfriday.app.assistant.context

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.core.common.Constants
import com.localfriday.app.domain.memory.ConversationRepository
import com.localfriday.app.domain.model.ChatMessage
import com.localfriday.app.domain.tool.Tokenizer
import javax.inject.Inject

class ContextBuilder @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val tokenizer: Tokenizer
) {
    data class Context(
        val recentConversations: List<ChatMessage>,
        val sessionId: String
    )

    suspend fun build(sessionId: String): AppResult<Context> {
        // Fetch up to 50 recent conversations to apply sliding window
        val conversationsResult = conversationRepository.getRecentBySession(
            sessionId,
            50
        )

        // TODO(v1): Fetch knowledge from KnowledgeRepository and include it in Context

        return when (conversationsResult) {
            is AppResult.Success -> {
                val slidingWindow = applyTokenSlidingWindow(conversationsResult.data)
                AppResult.Success(
                    Context(
                        recentConversations = slidingWindow,
                        sessionId = sessionId
                    )
                )
            }
            is AppResult.Failure -> AppResult.Failure(conversationsResult.error)
        }
    }

    private fun applyTokenSlidingWindow(messages: List<ChatMessage>): List<ChatMessage> {
        var currentTokens = 0
        val maxTokens = 3500 // Safe buffer under 4096 tokens
        val selectedMessages = mutableListOf<ChatMessage>()
        
        // messages is chronological (oldest first). We iterate from newest (end) to oldest.
        for (message in messages.reversed()) {
            val msgTokens = tokenizer.sizeInTokens(message.content) + 10 // overhead for tags
            if (currentTokens + msgTokens > maxTokens) {
                break
            }
            currentTokens += msgTokens
            selectedMessages.add(message)
        }
        
        // Restore chronological order
        return selectedMessages.reversed()
    }
}
