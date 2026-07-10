package com.kosmos.app.data.local.repository.fake


import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage

class FakeConversationRepository : ConversationRepository {
    private val messages = mutableListOf<ChatMessage>()

    override suspend fun save(message: ChatMessage): AppResult<Unit> {
        messages.add(message)
        return AppResult.Success(Unit)
    }

    override suspend fun getRecentBySession(
        sessionId: String,
        limit: Int
    ): AppResult<List<ChatMessage>> {
        val filtered = messages.filter { it.sessionId == sessionId }
            .sortedByDescending { it.createdAt }
            .take(limit)
            .reversed()
        return AppResult.Success(filtered)
    }

    override suspend fun getPagedBySession(sessionId: String, offset: Int, limit: Int): AppResult<List<ChatMessage>> {
        val data = messages.filter { it.sessionId == sessionId }.sortedByDescending { it.createdAt }.drop(offset).take(limit)
        return AppResult.Success(data)
    }
}
