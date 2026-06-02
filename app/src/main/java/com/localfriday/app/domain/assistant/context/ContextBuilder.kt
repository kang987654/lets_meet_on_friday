package com.localfriday.app.domain.assistant.context

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.core.common.Constants
import com.localfriday.app.domain.memory.ConversationRepository
import com.localfriday.app.domain.model.ChatMessage
import javax.inject.Inject

class ContextBuilder @Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    data class Context(
        val recentConversations: List<ChatMessage>,
        val sessionId: String
    )

    suspend fun build(sessionId: String): AppResult<Context> {
        // Fetch the recent conversations
        val conversationsResult = conversationRepository.getRecentBySession(
            sessionId,
            Constants.MAX_CONVERSATION_TURNS
        )

        // TODO(v1): Fetch knowledge from KnowledgeRepository and include it in Context

        return when (conversationsResult) {
            is AppResult.Success -> AppResult.Success(
                Context(
                    recentConversations = conversationsResult.data,
                    sessionId = sessionId
                )
            )
            is AppResult.Failure -> AppResult.Failure(conversationsResult.error)
        }
    }
}
