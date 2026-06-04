package com.localfriday.app.domain.assistant.agent

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.model.SearchRequest
import com.localfriday.app.domain.tool.WebSearchTool
import javax.inject.Inject

class SearchAgent @Inject constructor(
    private val webSearchTool: WebSearchTool
) {
    suspend fun executeSearch(query: String): AppResult<String> {
        val request = SearchRequest(query = query, maxResults = 3)
        return when (val res = webSearchTool.search(request)) {
            is AppResult.Success -> {
                val resultsText = res.data.joinToString("\n\n") { item ->
                    "Title: ${item.title}\nURL: ${item.url}\nSnippet: ${item.snippet}"
                }
                val formattedContext = if (resultsText.isNotBlank()) {
                    "다음은 사용자의 질문에 대한 웹 검색 결과입니다.\n\n$resultsText\n\n위 검색 결과를 바탕으로 사용자의 질문에 답변해 주세요."
                } else {
                    "웹 검색을 수행했으나 관련된 결과를 찾을 수 없습니다."
                }
                AppResult.Success(formattedContext)
            }
            is AppResult.Failure -> {
                AppResult.Failure(res.error)
            }
        }
    }
}
