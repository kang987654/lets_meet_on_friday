package com.localfriday.app.domain.memory

import androidx.paging.PagingSource
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.core.common.Constants
import com.localfriday.app.domain.model.ChatMessage

/**
 * [v0] 대화 내역 저장소
 * 시간 변환이나 Enum 매핑과 같은 mapper 책임은 구현체(Impl)에서 일관되게 처리해야 합니다.
 */
interface ConversationRepository {
    suspend fun save(message: ChatMessage): AppResult<Unit>
    suspend fun getRecentBySession(
        sessionId: String,
        limit: Int = Constants.MAX_CONVERSATION_TURNS
    ): AppResult<List<ChatMessage>>
    fun getPagedBySession(sessionId: String): PagingSource<Int, ChatMessage>
}
