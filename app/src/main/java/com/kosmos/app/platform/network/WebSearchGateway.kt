package com.kosmos.app.platform.network

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.SearchRequest
import com.kosmos.app.domain.model.SearchResultItem
import com.kosmos.app.domain.tool.WebSearchTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSearchGateway @Inject constructor() : WebSearchTool {

    override suspend fun search(request: SearchRequest): AppResult<List<SearchResultItem>> = withContext(Dispatchers.IO) {
        try {
            // v1에서는 외부 클라우드 LLM API나 유료 검색 API 연동이 금지되어 있으므로,
            // 네트워크 지연을 모사한 Mock 데이터를 반환합니다.
            // 추후 Google Custom Search나 DuckDuckGo HTML 파싱(JSoup)으로 대체 가능합니다.
            delay(1500) // 네트워크 지연 모사

            if (request.query.isBlank()) {
                return@withContext AppResult.Success(emptyList())
            }

            val mockResults = listOf(
                SearchResultItem(
                    title = "'${request.query}'에 대한 위키백과 검색 결과",
                    url = "https://ko.wikipedia.org/wiki/${request.query}",
                    snippet = "${request.query}와 관련된 자세한 설명과 역사적 배경을 제공하는 문서입니다."
                ),
                SearchResultItem(
                    title = "최신 뉴스: ${request.query}",
                    url = "https://news.example.com/search?q=${request.query}",
                    snippet = "${request.query}에 대한 가장 빠른 최신 이슈와 동향을 확인할 수 있는 뉴스 기사입니다."
                ),
                SearchResultItem(
                    title = "${request.query} - 공식 웹사이트",
                    url = "https://www.example.com/${request.query}",
                    snippet = "${request.query}의 공식 홈페이지에서 제공하는 정확한 정보와 가이드라인입니다."
                )
            ).take(request.maxResults)

            AppResult.Success(mockResults)
        } catch (e: Exception) {
            e.printStackTrace()
            // 에러 시 로컬 Fallback이 가능하도록 AppError.SearchError 반환
            AppResult.Failure(AppError.SearchError(e.message ?: "Unknown search error"))
        }
    }
}
