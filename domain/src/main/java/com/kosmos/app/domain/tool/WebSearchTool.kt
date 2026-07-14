package com.kosmos.app.domain.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.SearchRequest
import com.kosmos.app.domain.model.SearchResultItem

/**
 * [v1] 확장: 웹 검색 도구
 */
interface WebSearchTool {
    suspend fun search(request: SearchRequest): AppResult<List<SearchResultItem>>
}
