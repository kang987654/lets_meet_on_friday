package com.kosmos.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kosmos.app.data.local.db.entity.EpisodeEntity
import com.kosmos.app.data.local.db.entity.KnowledgeEntity
import com.kosmos.app.data.local.db.entity.MemoryVectorEntity

/**
 * [MemoryVectorDao]
 * 기억 뜻 벡터(`memory_vector`, 0.41.3) — 대상은 메모 전부와 요약된(SUMMARIZED) 지난 대화.
 *
 * [WHY] 세기·고르기는 대상 표와 맞붙여 한다 — 대상이 지워졌는데 벡터만 남은 행(무효화를 빠뜨린 경로)이 "준비 끝"으로 세지지 않게.
 */
@Dao
interface MemoryVectorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vectors: List<MemoryVectorEntity>)

    @Query("DELETE FROM memory_vector WHERE kind = :kind AND refId = :refId")
    suspend fun delete(kind: String, refId: String)


    @Query("DELETE FROM memory_vector")
    suspend fun deleteAll()

    @Query("DELETE FROM memory_vector WHERE modelId != :modelId")
    suspend fun deleteNotOf(modelId: String)

    @Query("SELECT (SELECT COUNT(*) FROM knowledge_note) + (SELECT COUNT(*) FROM episode WHERE status = 'SUMMARIZED')")
    suspend fun targetCount(): Int

    @Query(
        "SELECT (SELECT COUNT(*) FROM memory_vector v JOIN knowledge_note n ON v.kind = 'note' AND v.refId = n.id WHERE v.modelId = :modelId) + " +
            "(SELECT COUNT(*) FROM memory_vector v JOIN episode e ON v.kind = 'episode' AND v.refId = e.id WHERE v.modelId = :modelId AND e.status = 'SUMMARIZED')"
    )
    suspend fun embeddedCount(modelId: String): Int

    @Query(
        "SELECT * FROM knowledge_note n WHERE NOT EXISTS " +
            "(SELECT 1 FROM memory_vector v WHERE v.kind = 'note' AND v.refId = n.id AND v.modelId = :modelId) ORDER BY n.createdAt DESC LIMIT :limit"
    )
    suspend fun notesWithoutVector(modelId: String, limit: Int): List<KnowledgeEntity>

    @Query(
        "SELECT * FROM episode e WHERE e.status = 'SUMMARIZED' AND NOT EXISTS " +
            "(SELECT 1 FROM memory_vector v WHERE v.kind = 'episode' AND v.refId = e.id AND v.modelId = :modelId) ORDER BY e.createdAt DESC LIMIT :limit"
    )
    suspend fun episodesWithoutVector(modelId: String, limit: Int): List<EpisodeEntity>

    @Query("SELECT * FROM memory_vector WHERE modelId = :modelId")
    suspend fun vectorsOf(modelId: String): List<MemoryVectorEntity>

    @Query("SELECT * FROM memory_vector WHERE modelId = :modelId AND kind = :kind AND refId IN (:refIds)")
    suspend fun vectorsOf(modelId: String, kind: String, refIds: List<String>): List<MemoryVectorEntity>
}
