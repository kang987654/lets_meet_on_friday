package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [SearchWikipediaGroundingTest]
 * 위키 결과에 근거 고정 지침이 붙는 계약을 고정합니다.
 *
 * [WHY] 실기기에서 모델이 요약에 없는 데뷔일을 지어내 답했다(2026-08-13). 툴 회신 턴은
 * 턴 지침이 사라지는 턴이라 데이터 옆 지침이 유일한 근거리 주입 지점이고, 그 문구가 조용히
 * 빠지면 환각 방지가 통째로 사라진다 — MemoryPipelineIntegrationTest 가 SearchMemory 의
 * 같은 계약을 고정하는 것과 같은 이유다.
 */
@RunWith(RobolectricTestRunner::class)
class SearchWikipediaGroundingTest {

    private val useCase = mockk<com.kosmos.app.domain.usecase.QueryWikipediaUseCase>()
    private val executor = SearchWikipediaToolExecutor(useCase)

    private fun args(json: String) = ToolArguments(JSONObject(json))

    @Test
    fun `성공 결과의 data 끝에 근거 고정 지침이 붙는다`() = runBlocking {
        coEvery { useCase(any(), any()) } returns AppResult.Success(
            "Title: Aespa\n--- SUMMARY ---\naespa(에스파)는 대한민국의 4인조 다국적 걸그룹이다."
        )

        val result = executor.execute(args("""{"topic":"에스파","lang":"ko"}"""), "s1")

        val json = JSONObject(result)
        assertEquals("success", json.getString("status"))
        val data = json.getString("data")
        assertTrue("본문이 지침보다 앞에 있어야 한다", data.startsWith("Title: Aespa"))
        assertTrue("근거 고정 지침이 없다", data.endsWith(SearchWikipediaToolExecutor.GROUNDING_GUIDANCE))
        assertTrue("지어내기 금지 문구가 없다", data.contains("절대 지어내지 마세요"))
    }

    @Test
    fun `실패 결과에는 지침을 붙이지 않는다`() = runBlocking {
        coEvery { useCase(any(), any()) } returns AppResult.Failure(
            com.kosmos.app.core.common.AppError.NetworkUnavailable("offline")
        )

        val result = executor.execute(args("""{"topic":"에스파","lang":"ko"}"""), "s1")

        val json = JSONObject(result)
        assertEquals("error", json.getString("status"))
        assertFalse(result.contains("절대 지어내지 마세요"))
    }
}
