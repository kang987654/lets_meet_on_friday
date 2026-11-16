package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Tags
import androidx.room.withTransaction
import com.kosmos.app.data.local.db.KosmosDatabase
import com.kosmos.app.data.local.db.dao.MemoryVectorDao
import com.kosmos.app.data.local.db.entity.KnowledgeEntity
import com.kosmos.app.data.local.db.entity.MemoryVectorEntity
import com.kosmos.app.domain.library.Int8Vector
import com.kosmos.app.domain.memory.MemoryEmbedding
import com.kosmos.app.domain.memory.MemorySemanticStatus
import com.kosmos.app.domain.memory.MemoryVectorInput
import com.kosmos.app.domain.memory.MemoryVectorKey
import com.kosmos.app.domain.memory.MemoryVectorRepository
import com.kosmos.app.domain.model.KnowledgeNote
import javax.inject.Inject

/**
 * [MemoryVectorRepositoryImpl]
 * 기억 뜻 벡터의 Room 구현입니다 (0.41.3) — `memory_vector`.
 *
 * ### Architecture Context
 * - **Layer**: Data (Repository) — [MemoryVectorDao]. 입력 글자는 도메인 [MemoryEmbedding](exp80 형식).
 */
class MemoryVectorRepositoryImpl @Inject constructor(
    private val db: KosmosDatabase,
    private val dao: MemoryVectorDao
) : MemoryVectorRepository {

    override suspend fun status(modelId: String?): AppResult<MemorySemanticStatus> = dbRead(TABLE, "status") {
        MemorySemanticStatus(modelId, dao.targetCount(), if (modelId == null) 0 else dao.embeddedCount(modelId))
    }

    override suspend fun pending(modelId: String, limit: Int): AppResult<List<MemoryVectorInput>> = dbRead(TABLE, "pending") {
        val notes = dao.notesWithoutVector(modelId, limit).map { e -> MemoryVectorInput(MemoryVectorKey.note(e.id), noteInput(e)) }
        val rest = limit - notes.size
        val episodes = if (rest <= 0) emptyList() else dao.episodesWithoutVector(modelId, rest).map { e ->
            MemoryVectorInput(MemoryVectorKey.episode(e.id), MemoryEmbedding.episodeInput(e.toDomain()))
        }
        notes + episodes
    }

    override suspend fun save(modelId: String, vectors: List<Pair<MemoryVectorKey, Int8Vector>>): AppResult<Unit> = dbWrite(TABLE, "save") {
        dao.deleteNotOf(modelId)
        dao.insert(vectors.map { (key, v) -> MemoryVectorEntity(key.kind.code, key.refId, modelId, v.toBytes()) })
    }

    override suspend fun saveIfCurrent(modelId: String, vectors: List<Pair<MemoryVectorInput, Int8Vector>>): AppResult<Unit> =
        dbWrite(TABLE, "saveIfCurrent") {
            db.withTransaction {
                val current = vectors.filter { (input, _) -> currentInput(input.key) == input.input }
                dao.deleteNotOf(modelId)
                dao.insert(current.map { (input, v) -> MemoryVectorEntity(input.key.kind.code, input.key.refId, modelId, v.toBytes()) })
            }
        }

    /** 대상의 지금 임베딩 입력 — 지워졌거나(요약 전으로 돌아간 대화 포함) 없으면 null. */
    private suspend fun currentInput(key: MemoryVectorKey): String? = when (key.kind) {
        MemoryVectorKey.Kind.NOTE -> dao.note(key.refId)?.let { noteInput(it) }
        MemoryVectorKey.Kind.EPISODE -> dao.summarizedEpisode(key.refId)?.let { MemoryEmbedding.episodeInput(it.toDomain()) }
    }

    private fun noteInput(e: KnowledgeEntity): String = MemoryEmbedding.noteInput(
        KnowledgeNote(id = e.id, content = e.content, tags = Tags.decode(e.tags), createdAt = e.createdAt, updatedAt = e.updatedAt)
    )

    override suspend fun vectors(modelId: String): AppResult<Map<MemoryVectorKey, Int8Vector>> = dbRead(TABLE, "vectors") {
        dao.vectorsOf(modelId).mapNotNull { e ->
            val kind = MemoryVectorKey.Kind.of(e.kind) ?: return@mapNotNull null
            val vector = Int8Vector.fromBytes(e.vector) ?: return@mapNotNull null
            MemoryVectorKey(kind, e.refId) to vector
        }.toMap()
    }

    override suspend fun vectorsFor(modelId: String, keys: Collection<MemoryVectorKey>): AppResult<Map<MemoryVectorKey, Int8Vector>> =
        dbRead(TABLE, "vectorsFor") {
            // [WHY] SQLite 의 바인딩 변수 상한(오래된 기기 999)을 넘지 않게 묶어서 읽는다 — 후보는 종류별 최대 500건이지만 상한은 바뀔 수 있다.
            keys.groupBy { it.kind }.flatMap { (kind, ofKind) ->
                ofKind.map { it.refId }.chunked(IN_CHUNK).flatMap { ids -> dao.vectorsOf(modelId, kind.code, ids) }
            }.mapNotNull { e ->
                val kind = MemoryVectorKey.Kind.of(e.kind) ?: return@mapNotNull null
                val vector = Int8Vector.fromBytes(e.vector) ?: return@mapNotNull null
                MemoryVectorKey(kind, e.refId) to vector
            }.toMap()
        }

    override suspend fun clear(): AppResult<Unit> = dbWrite(TABLE, "clear") { dao.deleteAll() }

    private companion object {
        const val TABLE = "memory_vector"

        /** 한 번에 넘기는 키 수 — SQLite 바인딩 변수 상한(999)보다 작게. */
        const val IN_CHUNK = 900
    }
}
