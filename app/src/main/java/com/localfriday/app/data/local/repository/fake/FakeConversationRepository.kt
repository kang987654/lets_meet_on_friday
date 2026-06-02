package com.localfriday.app.data.local.repository.fake

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.memory.ConversationRepository
import com.localfriday.app.domain.model.ChatMessage

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

    override fun getPagedBySession(sessionId: String): PagingSource<Int, ChatMessage> {
        return object : PagingSource<Int, ChatMessage>() {
            override fun getRefreshKey(state: PagingState<Int, ChatMessage>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChatMessage> {
                val data = messages.filter { it.sessionId == sessionId }.sortedByDescending { it.createdAt }
                return LoadResult.Page(
                    data = data,
                    prevKey = null,
                    nextKey = null
                )
            }
        }
    }
}
