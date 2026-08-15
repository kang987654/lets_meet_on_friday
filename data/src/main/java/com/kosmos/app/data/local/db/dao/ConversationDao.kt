package com.kosmos.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kosmos.app.data.local.db.entity.ConversationEntity

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversation WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentBySession(sessionId: String, limit: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversation WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedBySession(sessionId: String, offset: Int, limit: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversation WHERE episodeId = :episodeId ORDER BY createdAt ASC")
    suspend fun getByEpisode(episodeId: String): List<ConversationEntity>

    /**
     * 에피소드 미배정 메시지 — 강제종료 등으로 배정을 놓친 행. catch-up 이 시간순으로 소급
     * 배정한다 (ADR-022).
     */
    @Query("SELECT * FROM conversation WHERE episodeId IS NULL ORDER BY createdAt ASC")
    suspend fun getUnassigned(): List<ConversationEntity>

    /**
     * 세션 무관 연속 타임라인 페이징 (시안 A′ — 세션은 UX 개념이 아니다).
     *
     * [WHY] `beforeTs`(앵커) 이전 행만 본다 — 페이징 집합을 불변으로 만들어, 새 메시지가
     * 들어와도 offset 키가 밀리는 중복/누락이 원천 차단된다. 앵커 이후는 라이브 테일이
     * 담당한다 (ChatViewModel).
     */
    @Query("SELECT * FROM conversation WHERE createdAt < :beforeTs ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedAll(beforeTs: Long, offset: Int, limit: Int): List<ConversationEntity>

    /** [ts] 이후(포함) 메시지 수 — 에피소드 원문 점프의 타임라인 인덱스 계산용. */
    @Query("SELECT COUNT(*) FROM conversation WHERE createdAt >= :ts")
    suspend fun countNewerThan(ts: Long): Int

    /** 에피소드 소급 배정 (catch-up). REPLACE insert 는 전체 행을 요구하므로 부분 UPDATE 를 둔다. */
    @Query("UPDATE conversation SET episodeId = :episodeId WHERE id = :messageId")
    suspend fun assignEpisode(messageId: String, episodeId: String)
}
