package com.localfriday.app.domain.tool

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.model.SearchRequest
import com.localfriday.app.domain.model.SearchResultItem

/**
 * [v1] 확장: 웹 검색 도구
 */
interface WebSearchTool {
    suspend fun search(request: SearchRequest): AppResult<List<SearchResultItem>>
}
