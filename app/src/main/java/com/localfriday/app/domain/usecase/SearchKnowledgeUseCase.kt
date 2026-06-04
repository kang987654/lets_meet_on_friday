package com.localfriday.app.domain.usecase

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.memory.KnowledgeRepository
import com.localfriday.app.domain.model.KnowledgeNote
import javax.inject.Inject

class SearchKnowledgeUseCase @Inject constructor(
    private val repository: KnowledgeRepository
) {
    /**
     * @param query 검색어 (없으면 무시)
     * @param tags 태그 목록 (없으면 무시)
     * @param limit 결과 최대 개수
     */
    suspend operator fun invoke(
        query: String? = null,
        tags: List<String>? = null,
        limit: Int = 10
    ): AppResult<List<KnowledgeNote>> {
        val hasQuery = !query.isNullOrBlank()
        val hasTags = !tags.isNullOrEmpty()

        return when {
            hasQuery && !hasTags -> {
                repository.search(query!!, limit)
            }
            !hasQuery && hasTags -> {
                repository.searchByTags(tags!!, limit)
            }
            hasQuery && hasTags -> {
                // 둘 다 있는 경우: (임시 구현) Query로 찾은 것 중 태그 필터링
                when (val result = repository.search(query!!, limit)) {
                    is AppResult.Success -> {
                        val filtered = result.data.filter { note ->
                            tags!!.any { tag -> note.tags.contains(tag) }
                        }
                        AppResult.Success(filtered.take(limit))
                    }
                    is AppResult.Failure -> result
                }
            }
            else -> {
                repository.searchRecent(limit)
            }
        }
    }
}
