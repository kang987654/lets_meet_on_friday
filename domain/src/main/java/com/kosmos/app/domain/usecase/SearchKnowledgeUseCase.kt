package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.TextEmbedder
import com.kosmos.app.domain.model.KnowledgeNote
import javax.inject.Inject

class SearchKnowledgeUseCase @Inject constructor(
    private val repository: KnowledgeRepository,
    private val textEmbedder: TextEmbedder
) {
    /**
     * @param query 검색어 (없으면 무시)
     * @param tags 태그 목록 (없으면 무시)
     * @param limit 결과 최대 개수
     */
    suspend operator fun invoke(
        query: String? = null,
        tags: List<String>? = null,
        limit: Int = 3 // RAG용이므로 3개 정도로 축소
    ): AppResult<List<KnowledgeNote>> {
        val hasQuery = !query.isNullOrBlank()

        return if (hasQuery) {
            val embeddingResult = textEmbedder.embed(query as String)
            if (embeddingResult is AppResult.Success) {
                // Vector search (가장 정확한 방법)
                repository.searchByVector(embeddingResult.data, limit)
            } else {
                // Embedder 실패 시 Fallback으로 일반 텍스트 검색 수행
                repository.search(query, limit)
            }
        } else {
            // query가 없으면 최근 기록 반환
            repository.searchRecent(limit)
        }
    }
}
