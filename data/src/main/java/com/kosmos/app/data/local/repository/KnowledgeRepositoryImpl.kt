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

    override suspend fun save(note: KnowledgeNote): AppResult<Unit> = com.kosmos.app.core.common.runCatchingCancellable {
        val embeddingStr = note.embedding?.joinToString(",") ?: ""
        dao.insert(
            KnowledgeEntity(
                id = note.id,
                content = note.content,
                sourceSessionId = null, // 임시: 현재 KnowledgeNote에는 sourceSessionId가 없으므로 필요시 추가 확장
                // [WHY] 태그에 콤마가 들어오면 이 칼럼 형식이 표현할 수 없다. CSV 인코딩을
                // 소유한 계층이 그 불변식도 지킨다 (Tags KDoc 참조).
                tags = com.kosmos.app.core.common.Tags.normalizeAll(note.tags).joinToString(","),
                embedding = embeddingStr,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt
            )
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.DbWriteError("knowledge_note")) }
    )

    override suspend fun delete(noteId: String): AppResult<Unit> = com.kosmos.app.core.common.runCatchingCancellable {
        dao.delete(noteId)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.DbWriteError("knowledge_note")) }
    )

    override suspend fun search(query: String, limit: Int): AppResult<List<KnowledgeNote>> = com.kosmos.app.core.common.runCatchingCancellable {
        // [WHY] 빈 검색어는 '%%' 패턴이 되어 테이블 전체를 매칭한다. 그 결과 100건이
        // RAG 프롬프트로 쏟아져 컨텍스트 예산을 터뜨리므로 조회 전에 차단한다.
        if (query.isBlank()) {
            emptyList()
        } else {
            dao.search(com.kosmos.app.core.common.SqlLike.escape(query), limit).map { it.toDomain() }
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.SearchError(it.message ?: "search err")) }
    )

    override suspend fun searchRecent(limit: Int): AppResult<List<KnowledgeNote>> = com.kosmos.app.core.common.runCatchingCancellable {
        dao.searchRecent(limit).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.SearchError(it.message ?: "search err")) }
    )

    override suspend fun searchByTags(tags: List<String>, limit: Int): AppResult<List<KnowledgeNote>> = com.kosmos.app.core.common.runCatchingCancellable {
        // SQLite의 LIKE 검색을 위해 각 태그별로 검색 결과를 모은 후 중복을 제거 (간이 구현)
        val results = mutableSetOf<KnowledgeEntity>()
        for (tag in tags) {
            // [WHY] 저장 시와 같은 정규화를 적용해야 저장된 값과 형태가 맞는다. 콤마가 든
            // 검색어는 정규화 없이는 어떤 태그에도 매칭될 수 없다.
            val normalized = com.kosmos.app.core.common.Tags.normalize(tag)
            // 공백만인 태그는 구분자 사이의 빈 토큰에 매칭되므로 건너뛴다.
            if (normalized.isEmpty()) continue
            results.addAll(dao.searchByTags(com.kosmos.app.core.common.SqlLike.escape(normalized), limit))
        }
        results.map { it.toDomain() }.take(limit)
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.SearchError(it.message ?: "search err")) }
    )

    // 코사인 유사도 계산 지원 (RAG)
    // [WHY] 최대 1000행의 CSV 파싱+코사인 연산이 호출자 디스패처(메인 가능)에서 돌지 않도록 Default로 이동한다.
    override suspend fun searchByVector(queryEmbedding: FloatArray, limit: Int): AppResult<List<KnowledgeNote>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
      com.kosmos.app.core.common.runCatchingCancellable {
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
    }
    
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
