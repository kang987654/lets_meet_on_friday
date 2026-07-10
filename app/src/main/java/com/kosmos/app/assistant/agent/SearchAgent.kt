package com.kosmos.app.assistant.agent

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.SearchRequest
import com.kosmos.app.domain.tool.WebSearchTool
import javax.inject.Inject

/**
 * [SearchAgent]
 * LLM이 파싱한 검색 요청(SearchOutput)을 실제 웹/디바이스 검색과 연동하는 에이전트 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Agent Action)
 * - **Dependencies**: [SearchTool]
 *
 * ### Key Flow
 * 1. Orchestrator로부터 모델 파싱 결과인 [ModelOutput.SearchOutput] 수신
 * 2. [SearchTool]을 호출하여 주어진 쿼리에 대한 결과 텍스트(Context) 획득
 * 3. 결과를 다시 Orchestrator에 반환하여 모델이 컨텍스트를 바탕으로 답변을 재생성하도록 지원
 */
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
