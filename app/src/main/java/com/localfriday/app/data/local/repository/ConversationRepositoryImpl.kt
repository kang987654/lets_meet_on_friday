package com.localfriday.app.data.local.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.core.common.Constants
import com.localfriday.app.data.local.db.dao.ConversationDao
import com.localfriday.app.data.local.db.entity.ConversationEntity
import com.localfriday.app.domain.memory.ConversationRepository
import com.localfriday.app.domain.model.ChatMessage
import com.localfriday.app.domain.model.InputType
import javax.inject.Inject

class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao
) : ConversationRepository {

    override suspend fun save(message: ChatMessage): AppResult<Unit> {
        return try {
            val entity = ConversationEntity(
                id = message.id,
                sessionId = message.sessionId,
                role = message.role.name,
                content = message.content,
                inputType = message.inputType.name,
                searchUsed = message.searchUsed,
                createdAt = message.createdAt
            )
            conversationDao.insert(entity)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(AppError.DbWriteError("conversation"))
        }
    }

    override suspend fun getRecentBySession(
        sessionId: String,
        limit: Int
    ): AppResult<List<ChatMessage>> {
        return try {
            val entities = conversationDao.getRecentBySession(sessionId, limit)
            val messages = entities.map { it.toDomain() }
            AppResult.Success(messages.reversed())
        } catch (e: Exception) {
            AppResult.Failure(AppError.DbWriteError("conversation"))
        }
    }

    override fun getPagedBySession(sessionId: String): PagingSource<Int, ChatMessage> {
        val originalSource = conversationDao.getPagedBySession(sessionId)
        return MappedPagingSource(originalSource) { it.toDomain() }
    }
}

fun ConversationEntity.toDomain(): ChatMessage {
    return ChatMessage(
        id = this.id,
        sessionId = this.sessionId,
        role = try { ChatMessage.Role.valueOf(this.role) } catch (e: Exception) { ChatMessage.Role.USER },
        content = this.content,
        inputType = try { InputType.valueOf(this.inputType) } catch (e: Exception) { InputType.TEXT },
        searchUsed = this.searchUsed,
        createdAt = this.createdAt
    )
}

class MappedPagingSource<Key : Any, Value : Any, ToValue : Any>(
    private val originalSource: PagingSource<Key, Value>,
    private val mapper: (Value) -> ToValue
) : PagingSource<Key, ToValue>() {
    override fun getRefreshKey(state: PagingState<Key, ToValue>): Key? {
        return null 
    }
    
    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, ToValue> {
        return when (val result = originalSource.load(params)) {
            is LoadResult.Page -> LoadResult.Page(
                data = result.data.map(mapper),
                prevKey = result.prevKey,
                nextKey = result.nextKey,
                itemsBefore = result.itemsBefore,
                itemsAfter = result.itemsAfter
            )
            is LoadResult.Error -> LoadResult.Error(result.throwable)
            is LoadResult.Invalid -> LoadResult.Invalid()
        }
    }
}
