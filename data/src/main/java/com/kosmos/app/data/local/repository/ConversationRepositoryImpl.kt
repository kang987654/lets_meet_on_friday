package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.enumOrDefault
import com.kosmos.app.data.local.db.dao.ConversationDao
import com.kosmos.app.data.local.db.entity.ConversationEntity
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import javax.inject.Inject

class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao
) : ConversationRepository {

    override suspend fun save(message: ChatMessage): AppResult<Unit> = dbWrite(TABLE, "메시지 저장") {
        conversationDao.insert(
            ConversationEntity(
                id = message.id,
                sessionId = message.sessionId,
                role = message.role.name,
                content = message.content,
                inputType = message.inputType.name,
                searchUsed = message.searchUsed,
                createdAt = message.createdAt,
                episodeId = message.episodeId,
                // [WHY] 빈 목록은 NULL 로 — "회수 없음"과 "빈 CSV"를 구분할 이유가 없고
                // NULL 이 마이그레이션 기본값과 일치한다.
                recallEpisodeIds = message.recallEpisodeIds
                    .takeIf { it.isNotEmpty() }?.joinToString(",")
            )
        )
    }

    override suspend fun getRecentBySession(
        sessionId: String,
        limit: Int
    ): AppResult<List<ChatMessage>> = read("최근 메시지 조회") {
        conversationDao.getRecentBySession(sessionId, limit).map { it.toDomain() }.reversed()
    }

    override suspend fun getPagedBySession(sessionId: String, offset: Int, limit: Int): AppResult<List<ChatMessage>> =
        read("페이징 메시지 조회") { conversationDao.getPagedBySession(sessionId, offset, limit).map { it.toDomain() } }

    override suspend fun getByEpisode(episodeId: String): AppResult<List<ChatMessage>> =
        read("에피소드 메시지 조회") { conversationDao.getByEpisode(episodeId).map { it.toDomain() } }

    override suspend fun getUnassigned(): AppResult<List<ChatMessage>> =
        read("미배정 메시지 조회") { conversationDao.getUnassigned().map { it.toDomain() } }

    override suspend fun assignEpisode(messageId: String, episodeId: String): AppResult<Unit> =
        dbWrite(TABLE, "에피소드 배정") { conversationDao.assignEpisode(messageId, episodeId) }

    override suspend fun getPagedAll(beforeTs: Long, offset: Int, limit: Int): AppResult<List<ChatMessage>> =
        read("타임라인 페이징 조회") { conversationDao.getPagedAll(beforeTs, offset, limit).map { it.toDomain() } }

    override suspend fun countNewerThan(ts: Long): AppResult<Int> =
        read("타임라인 인덱스 계산") { conversationDao.countNewerThan(ts) }

    override suspend fun countOlderThan(beforeTs: Long): AppResult<Int> =
        read("타임라인 총수 계산") { conversationDao.countOlderThan(beforeTs) }

    override suspend fun countByInputTypeSince(inputType: InputType, sinceTs: Long): AppResult<Int> =
        read("입력 유형 카운트") { conversationDao.countByInputTypeSince(inputType.name, sinceTs) }

    private inline fun <T> read(what: String, block: () -> T): AppResult<T> = dbRead(TABLE, what, block)

    private companion object {
        const val TABLE = "conversation"
    }
}

fun ConversationEntity.toDomain(): ChatMessage {
    return ChatMessage(
        id = this.id,
        sessionId = this.sessionId,
        role = enumOrDefault(this.role, ChatMessage.Role.USER),
        content = this.content,
        inputType = enumOrDefault(this.inputType, InputType.TEXT),
        searchUsed = this.searchUsed,
        createdAt = this.createdAt,
        episodeId = this.episodeId,
        // [WHY] 저장과 대칭 — NULL/빈 문자열 모두 빈 목록으로. 여기서 빠뜨리면 thinkingProcess
        // 처럼 "저장은 되는데 재로드에서 소실"되는 결함이 재현된다 (매퍼 왕복 테스트가 고정).
        recallEpisodeIds = this.recallEpisodeIds
            ?.split(",")?.mapNotNull { it.trim().ifEmpty { null } }?.distinct() ?: emptyList()
    )
}

