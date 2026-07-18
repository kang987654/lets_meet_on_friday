package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.dao.KnowledgeDao
import com.kosmos.app.data.local.db.entity.KnowledgeEntity
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.model.KnowledgeNote
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import javax.inject.Inject

class KnowledgeRepositoryImpl @Inject constructor(
    private val dao: KnowledgeDao
) : KnowledgeRepository {

    override suspend fun save(note: KnowledgeNote): AppResult<Unit> = runCatching {
        val embeddingStr = note.embedding?.joinToString(",") ?: ""
        dao.insert(
            KnowledgeEntity(
                id = note.id,
                content = note.content,
                sourceSessionId = null, // 임시: 현재 KnowledgeNote에는 sourceSessionId가 없으므로 필요시 추가 확장
                tags = note.tags.joinToString(","),
                embedding = embeddingStr,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt
            )
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.DbWriteError("knowledge_note")) }
    )

    override suspend fun delete(noteId: String): AppResult<Unit> = runCatching {
        dao.delete(noteId)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.DbWriteError("knowledge_note")) }
    )

    override suspend fun search(query: String, limit: Int): AppResult<List<KnowledgeNote>> = runCatching {
        // 기존 LIKE 검색
        dao.search(query, limit).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.SearchError(it.message ?: "search err")) }
    )

    override suspend fun searchRecent(limit: Int): AppResult<List<KnowledgeNote>> = runCatching {
        dao.searchRecent(limit).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.SearchError(it.message ?: "search err")) }
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
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.SearchError(it.message ?: "search err")) }
    )

    // 코사인 유사도 계산 지원 (RAG)
    override suspend fun searchByVector(queryEmbedding: FloatArray, limit: Int): AppResult<List<KnowledgeNote>> = runCatching {
        val allEntities = dao.searchRecent(1000) // 모두 가져옴 (모바일 특성상 데이터가 많지 않음)
        
        val scoredList = allEntities.mapNotNull { entity ->
            val entityEmbedding = entity.embedding.split(",")
                .mapNotNull { it.toFloatOrNull() }
                .toFloatArray()
                
            if (entityEmbedding.size == queryEmbedding.size && entityEmbedding.isNotEmpty()) {
                val score = cosineSimilarity(queryEmbedding, entityEmbedding)
                Pair(entity.toDomain(), score)
            } else {
                null
            }
        }
        
        // 코사인 유사도가 높은 순으로 정렬 후 limit 반환 (유사도 0.3 이상 필터링 가능, 일단 모두)
        scoredList
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.SearchError(it.message ?: "vector search err")) }
    )
    
    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        return if (normA == 0f || normB == 0f) 0f else (dotProduct / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble()))).toFloat()
    }

    override fun getPagedData(): Flow<PagingData<KnowledgeNote>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false)
        ) {
            dao.getPaged()
        }.flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
    }

    private fun KnowledgeEntity.toDomain(): KnowledgeNote {
        val floatArr = embedding.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
        return KnowledgeNote(
            id = id,
            content = content,
            tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            embedding = if (floatArr.isNotEmpty()) floatArr else null,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
