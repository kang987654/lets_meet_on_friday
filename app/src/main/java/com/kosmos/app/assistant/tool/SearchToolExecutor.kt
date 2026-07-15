package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.SearchRequest
import com.kosmos.app.domain.tool.WebSearchTool
import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.assistant.approval.ApprovalRequest
import javax.inject.Inject

class SearchToolExecutor @Inject constructor(
    private val webSearchTool: WebSearchTool,
    private val approvalCoordinator: ApprovalCoordinator
) : ToolExecutor {
    override val name: String = "SearchWeb"

    override suspend fun execute(args: Map<String, Any>, sessionId: String): String {
        val query = args["query"] as? String ?: ""
        
        val request = ApprovalRequest(
            sessionId = sessionId,
            title = "웹 검색 승인",
            description = "검색어: '$query'"
        )
        
        val approved = approvalCoordinator.requireApproval(request)
        if (!approved) {
            return "{\"status\": \"error\", \"message\": \"사용자가 검색을 취소했습니다.\"}"
        }

        val searchRequest = SearchRequest(query = query, maxResults = 3)
        return when (val res = webSearchTool.search(searchRequest)) {
            is AppResult.Success -> {
                val resultsText = res.data.joinToString("\n\n") { item ->
                    "Title: ${item.title}\nURL: ${item.url}\nSnippet: ${item.snippet}"
                }
                val formattedContext = if (resultsText.isNotBlank()) {
                    "다음은 사용자의 질문에 대한 웹 검색 결과입니다.\n\n$resultsText"
                } else {
                    "웹 검색을 수행했으나 관련된 결과를 찾을 수 없습니다."
                }
                "{\"status\": \"success\", \"data\": \"$formattedContext\"}"
            }
            is AppResult.Failure -> {
                "{\"status\": \"error\", \"message\": \"검색 중 오류 발생\"}"
            }
        }
    }
}
