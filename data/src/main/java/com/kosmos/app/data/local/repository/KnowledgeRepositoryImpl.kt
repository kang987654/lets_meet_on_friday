package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.dao.KnowledgeDao
import com.kosmos.app.data.local.db.entity.KnowledgeEntity
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.model.KnowledgeNote
import javax.inject.Inject
import com.kosmos.app.data.local.db.dao.MemoryVectorDao
import com.kosmos.app.domain.memory.MemoryVectorKey
import com.kosmos.app.core.common.SqlLike
import com.kosmos.app.core.common.Tags

/**
 * [KnowledgeRepositoryImpl]
 * 메모 저장소 Room 구현입니다.
 *
 * [WHY] 저장·삭제에서 그 메모의 뜻 벡터(`memory_vector`)를 지운다 — 내용이 바뀐 메모에 옛 벡터가 남으면 엉뚱한 뜻으로 찾힌다.
 * 지워진 벡터는 `MemoryEmbedder` 가 다음 조용한 때 다시 만든다(0.41.3).
 */
class KnowledgeRepositoryImpl @Inject constructor(
    private val dao: KnowledgeDao,
    private val vectors: MemoryVectorDao
) : KnowledgeRepository {

    override suspend fun save(note: KnowledgeNote): AppResult<Unit> = dbWrite(TABLE, "save") {
        dao.insert(
            KnowledgeEntity(
                id = note.id,
                content = note.content,
                sourceSessionId = null, // 임시: 현재 KnowledgeNote에는 sourceSessionId가 없으므로 필요시 추가 확장
                // [WHY] 태그에 콤마가 들어오면 이 칼럼 형식이 표현할 수 없다. CSV 인코딩을
                // 소유한 계층이 그 불변식도 지킨다 (Tags KDoc 참조).
                tags = Tags.encode(note.tags),
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                source = note.source
            )
        )
        vectors.delete(MemoryVectorKey.Kind.NOTE.code, note.id)
    }

    override suspend fun getById(noteId: String): AppResult<KnowledgeNote?> = dbRead(TABLE, "getById") {
        dao.getById(noteId)?.toDomain()
    }

    override suspend fun delete(noteId: String): AppResult<Unit> = dbWrite(TABLE, "delete") {
        dao.delete(noteId)
        vectors.delete(MemoryVectorKey.Kind.NOTE.code, noteId)
    }

    override suspend fun search(query: String, limit: Int): AppResult<List<KnowledgeNote>> = dbRead(TABLE, "search") {
        // [WHY] 빈 검색어는 '%%' 패턴이 되어 테이블 전체를 매칭한다. 그 결과 100건이
        // RAG 프롬프트로 쏟아져 컨텍스트 예산을 터뜨리므로 조회 전에 차단한다.
        if (query.isBlank()) {
            emptyList()
        } else {
            dao.search(SqlLike.escape(query), limit).map { it.toDomain() }
        }
    }

    override suspend fun searchRecent(limit: Int): AppResult<List<KnowledgeNote>> = dbRead(TABLE, "searchRecent") {
        dao.searchRecent(limit).map { it.toDomain() }
    }

    override suspend fun searchByTags(tags: List<String>, limit: Int): AppResult<List<KnowledgeNote>> = dbRead(TABLE, "searchByTags") {
        // SQLite의 LIKE 검색을 위해 각 태그별로 검색 결과를 모은 후 id 기준으로 중복을 제거 (간이 구현)
        val results = mutableListOf<KnowledgeEntity>()
        for (tag in tags) {
            // [WHY] 저장 시와 같은 정규화를 적용해야 저장된 값과 형태가 맞는다. 콤마가 든
            // 검색어는 정규화 없이는 어떤 태그에도 매칭될 수 없다.
            val normalized = Tags.normalize(tag)
            // 공백만인 태그는 구분자 사이의 빈 토큰에 매칭되므로 건너뛴다.
            if (normalized.isEmpty()) continue
            results.addAll(dao.searchByTags(SqlLike.escape(normalized), limit))
        }
        results.distinctBy { it.id }.map { it.toDomain() }.take(limit)
    }

    override suspend fun getNotes(offset: Int, limit: Int): AppResult<List<KnowledgeNote>> = dbRead(TABLE, "getNotes") {
            dao.getNotes(offset, limit).map { it.toDomain() }
    }

    private fun KnowledgeEntity.toDomain(): KnowledgeNote {
        return KnowledgeNote(
            id = id,
            content = content,
            tags = Tags.decode(tags),
            createdAt = createdAt,
            updatedAt = updatedAt,
            source = source
        )
    }

    private companion object {
        const val TABLE = "knowledge_note"
    }
}
