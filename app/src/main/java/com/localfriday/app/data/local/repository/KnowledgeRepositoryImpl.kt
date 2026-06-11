package com.localfriday.app.data.local.repository

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.data.local.db.dao.KnowledgeDao
import com.localfriday.app.data.local.db.entity.KnowledgeEntity
import com.localfriday.app.domain.memory.KnowledgeRepository
import com.localfriday.app.domain.model.KnowledgeNote

import javax.inject.Inject

class KnowledgeRepositoryImpl @Inject constructor(
    private val dao: KnowledgeDao
) : KnowledgeRepository {

    override suspend fun save(note: KnowledgeNote): AppResult<Unit> = runCatching {
        dao.insert(
            KnowledgeEntity(
                id = note.id,
                content = note.content,
                sourceSessionId = null, // 임시: 현재 KnowledgeNote에는 sourceSessionId가 없으므로 필요시 추가 확장
                tags = note.tags.joinToString(","),
                createdAt = note.createdAt,
                updatedAt = note.updatedAt
            )
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Failure(com.localfriday.app.core.common.AppError.DbWriteError("knowledge_note")) }
    )

    override suspend fun delete(noteId: String): AppResult<Unit> = runCatching {
        dao.delete(noteId)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Failure(com.localfriday.app.core.common.AppError.DbWriteError("knowledge_note")) }
    )

    override suspend fun search(query: String, limit: Int): AppResult<List<KnowledgeNote>> = runCatching {
        dao.search(query, limit).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(com.localfriday.app.core.common.AppError.SearchError(it.message ?: "search err")) }
    )

    override suspend fun searchRecent(limit: Int): AppResult<List<KnowledgeNote>> = runCatching {
        dao.searchRecent(limit).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(com.localfriday.app.core.common.AppError.SearchError(it.message ?: "search err")) }
    )

    override suspend fun searchByTags(tags: List<String>, limit: Int): AppResult<List<KnowledgeNote>> = runCatching {
        // SQLite의 LIKE 검색을 위해 각 태그별로 검색 결과를 모은 후 중복을 제거 (간이 구현)
        val results = mutableSetOf<KnowledgeEntity>()
        for (tag in tags) {
            results.addAll(dao.searchByTags(tag, limit))
        }
        results.map { it.toDomain() }.take(limit)
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(com.localfriday.app.core.common.AppError.SearchError(it.message ?: "search err")) }
    )

    override suspend fun getPagedData(offset: Int, limit: Int): AppResult<List<KnowledgeNote>> = runCatching {
        dao.getPaged(offset, limit).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(com.localfriday.app.core.common.AppError.DbReadError("knowledge_note")) }
    )

    private fun KnowledgeEntity.toDomain(): KnowledgeNote {
        return KnowledgeNote(
            id = id,
            content = content,
            tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
