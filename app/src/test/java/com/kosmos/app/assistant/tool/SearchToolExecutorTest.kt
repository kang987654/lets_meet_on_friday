package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.SearchRequest
import com.kosmos.app.domain.model.SearchResultItem
import com.kosmos.app.domain.tool.WebSearchTool
import com.kosmos.app.assistant.approval.ApprovalCoordinator
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchToolExecutorTest {

    class FakeWebSearchTool(val forceFailure: Boolean = false) : WebSearchTool {
        override suspend fun search(request: SearchRequest): AppResult<List<SearchResultItem>> {
            if (forceFailure) return AppResult.Failure(AppError.NetworkUnavailable("Network error"))
            return AppResult.Success(
                listOf(
                    SearchResultItem(
                        title = "Test Weather",
                        url = "http://test.com",
                        snippet = "Sunny day"
                    )
                )
            )
        }
    }

    @Test
    fun `test execute web search with approval`() = runBlocking {
        val coordinator = ApprovalCoordinator()
        val webSearchTool = FakeWebSearchTool()
        val executor = SearchToolExecutor(webSearchTool, coordinator)

        // Run execution in parallel since requireApproval suspends
        val resultDeferred = async {
            executor.execute(mapOf("query" to "seoul weather"), "test-session")
        }

        // Wait for coordinator to receive request and approve it
        kotlinx.coroutines.delay(100) 
        val pending = coordinator.pendingRequest.value
        assertTrue(pending != null)
        assertTrue(pending?.title == "웹 검색 승인")
        
        coordinator.approve()

        val result = resultDeferred.await()
        assertTrue("Result should be success json", result.contains("success"))
        assertTrue("Result should contain snippet", result.contains("Sunny day"))
    }

    @Test
    fun `test execute web search with rejection`() = runBlocking {
        val coordinator = ApprovalCoordinator()
        val webSearchTool = FakeWebSearchTool()
        val executor = SearchToolExecutor(webSearchTool, coordinator)

        val resultDeferred = async {
            executor.execute(mapOf("query" to "seoul weather"), "test-session")
        }

        kotlinx.coroutines.delay(100)
        coordinator.reject()

        val result = resultDeferred.await()
        assertTrue("Result should be error json when rejected", result.contains("error"))
        assertTrue("Result should mention cancellation", result.contains("취소했습니다"))
    }
}
