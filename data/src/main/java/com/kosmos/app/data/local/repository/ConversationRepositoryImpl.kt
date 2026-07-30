package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.data.local.db.dao.ConversationDao
import com.kosmos.app.data.local.db.entity.ConversationEntity
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
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
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.kosmos.app.core.logging.AppLogger.e("ConversationRepo", "메시지 저장 실패", e)
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
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.kosmos.app.core.logging.AppLogger.e("ConversationRepo", "최근 메시지 조회 실패", e)
            // [WHY] 읽기 실패는 DbReadError로 분류해야 오류 코드 매핑이 정확하다.
            AppResult.Failure(AppError.DbReadError("conversation"))
        }
    }

    override suspend fun getPagedBySession(sessionId: String, offset: Int, limit: Int): AppResult<List<ChatMessage>> {
        return try {
            val entities = conversationDao.getPagedBySession(sessionId, offset, limit)
            AppResult.Success(entities.map { it.toDomain() })
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.kosmos.app.core.logging.AppLogger.e("ConversationRepo", "페이징 메시지 조회 실패", e)
            AppResult.Failure(AppError.DbReadError("conversation"))
        }
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

