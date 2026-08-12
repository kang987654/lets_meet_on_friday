package com.kosmos.app.domain

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelTurn
import com.kosmos.app.domain.usecase.SummarizeScheduleUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SummarizeScheduleUseCaseTest]
 * 요약이 **부수 계산으로 나가는지**를 못박습니다.
 *
 * [WHY] 이 호출이 `oneShot` 이 아니면 시스템 지시와 sessionId 가 채팅과 달라 런타임이 캐시된
 * 채팅 대화를 파괴한다. 그러면 캘린더 탭을 열었다 돌아오는 것만으로 다음 채팅 질문이 전체
 * 프리필(툴 선언 ~2천 토큰 포함)을 다시 낸다 (ADR-010·014). 2026-08-12 실기기에서 이 계약이
 * 지켜지는 것을 확인했으므로(캘린더 왕복 후에도 16.3 자/초 유지) 회귀로 잃지 않게 고정한다.
 */
class SummarizeScheduleUseCaseTest {

    private val modelRunner: ModelRunner = mockk()
    private fun useCase() = SummarizeScheduleUseCase(modelRunner)

    @Test
    fun `요약은 일회성 프롬프트로 나가고 툴과 히스토리를 싣지 않는다`() = runBlocking {
        val prompt = slot<ChatPrompt>()
        coEvery { modelRunner.generate(capture(prompt), any()) } returns
            AppResult.Success(ModelTurn("오늘은 저녁 약속 하나가 있습니다."))

        useCase()(listOf(event()), ScheduleData.RangeType.TODAY)

        assertTrue("oneShot 이어야 캐시된 채팅 대화를 깨지 않는다", prompt.captured.oneShot)
        assertTrue("툴을 선언하면 프리필이 비싸진다", prompt.captured.enabledTools.isEmpty())
        assertTrue(prompt.captured.history.isEmpty())
    }

    @Test
    fun `일정 제목과 시각이 프롬프트에 들어간다`() = runBlocking {
        val prompt = slot<ChatPrompt>()
        coEvery { modelRunner.generate(capture(prompt), any()) } returns
            AppResult.Success(ModelTurn("요약"))

        useCase()(listOf(event()), ScheduleData.RangeType.WEEK)

        assertTrue(prompt.captured.currentInput.contains("저녁 약속"))
        assertTrue(prompt.captured.currentInput.contains("2026-08-12T20:00"))
        assertTrue("범위 표현이 반영돼야 한다", prompt.captured.currentInput.contains("이번 주"))
    }

    @Test
    fun `일정이 없으면 모델을 호출하지 않는다`() = runBlocking {
        // [WHY] 빈 목록에 추론을 돌리면 배터리와 발열만 쓰고 나오는 문장도 쓸모없다.
        val result = useCase()(emptyList(), ScheduleData.RangeType.TODAY)

        assertEquals("", (result as AppResult.Success).data)
        coVerify(exactly = 0) { modelRunner.generate(any(), any()) }
    }

    @Test
    fun `모델 실패는 실패로 올린다`() = runBlocking {
        // [WHY] 예전에는 null 로 내려서 호출자가 "아직 안 옴"과 "못 만듦"을 구분할 수 없었다.
        coEvery { modelRunner.generate(any(), any()) } returns
            AppResult.Failure(AppError.ModelNotReady("not ready"))

        val result = useCase()(listOf(event()), ScheduleData.RangeType.TODAY)

        assertTrue((result as AppResult.Failure).error is AppError.ModelNotReady)
    }

    private fun event() = CalendarEvent(
        id = "1",
        title = "저녁 약속",
        startIso = "2026-08-12T20:00",
        endIso = "2026-08-12T21:00"
    )
}
