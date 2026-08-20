package com.kosmos.app.domain

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelTurn
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.domain.usecase.GenerateBriefingUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GenerateBriefingUseCaseTest]
 * 브리핑 생성이 **부수 계산(oneShot)으로 나가는지**와 재료 직렬화·절단 계약을 못박습니다.
 *
 * [WHY] oneShot 이 아니면 시스템 지시와 sessionId 가 채팅과 달라 런타임이 캐시된 채팅
 * 대화를 파괴한다 (ADR-010·014, SummarizeEpisodeUseCaseTest 와 같은 계약).
 */
class GenerateBriefingUseCaseTest {

    private val modelRunner: ModelRunner = mockk()
    private val tokenizer: Tokenizer = mockk<Tokenizer>().also {
        io.mockk.every { it.sizeInTokens(any()) } answers { firstArg<String>().length / 2 }
    }

    private fun useCase() = GenerateBriefingUseCase(modelRunner, tokenizer)

    private fun event(title: String, iso: String = "2026-08-21T16:00:00") =
        CalendarEvent(id = title, title = title, startIso = iso, endIso = iso)

    private fun task(title: String) = TaskItem(
        id = title, title = title, isCompleted = false, createdAt = 0L
    )

    private fun episode(title: String, summary: String) = Episode(
        id = title, sessionId = "s1", status = EpisodeStatus.SUMMARIZED,
        title = title, summary = summary, tags = emptyList(),
        startAt = 0L, endAt = 1L, messageCount = 2, retryCount = 0,
        createdAt = 0L, updatedAt = 0L
    )

    private fun materials(
        events: List<CalendarEvent> = listOf(event("특강")),
        deviceCalendarFailed: Boolean = false,
        tasks: List<TaskItem> = listOf(task("b형 시험 준비")),
        episodes: List<Episode> = listOf(episode("자전거 자물쇠 메모", "비밀번호를 1234로 저장했다"))
    ) = GenerateBriefingUseCase.BriefingMaterials(
        dateLabel = "8월 21일 금요일",
        events = events,
        deviceCalendarFailed = deviceCalendarFailed,
        pendingTasks = tasks,
        recentEpisodes = episodes
    )

    @Test
    fun `브리핑은 일회성 프롬프트로 나가고 툴과 히스토리를 싣지 않는다`() = runBlocking {
        val prompt = slot<ChatPrompt>()
        coEvery { modelRunner.generate(capture(prompt), any()) } returns
            AppResult.Success(ModelTurn("좋은 아침이에요!"))

        useCase()(materials())

        assertTrue("oneShot 이어야 캐시된 채팅 대화를 깨지 않는다", prompt.captured.oneShot)
        assertTrue("툴을 선언하면 프리필이 비싸진다", prompt.captured.enabledTools.isEmpty())
        assertTrue(prompt.captured.history.isEmpty())
        assertEquals("morning-briefing", prompt.captured.sessionId)
    }

    @Test
    fun `재료가 한국어 표기로 프롬프트에 들어간다`() = runBlocking {
        val prompt = slot<ChatPrompt>()
        coEvery { modelRunner.generate(capture(prompt), any()) } returns
            AppResult.Success(ModelTurn("브리핑"))

        useCase()(materials())

        val input = prompt.captured.currentInput
        assertTrue(input.contains("8월 21일 금요일"))
        assertTrue("일정 시각은 한국어 표기(0.19.2 단일 출처)", input.contains("오후 4:00"))
        assertTrue(input.contains("b형 시험 준비"))
        assertTrue(input.contains("자전거 자물쇠 메모"))
    }

    @Test
    fun `기기 캘린더 실패는 프롬프트에 명시된다`() = runBlocking {
        // [WHY] 조용히 생략하면 "오늘 일정 없음"이 거짓이 된다 (PRD EC4).
        val prompt = slot<ChatPrompt>()
        coEvery { modelRunner.generate(capture(prompt), any()) } returns
            AppResult.Success(ModelTurn("브리핑"))

        useCase()(materials(deviceCalendarFailed = true))

        assertTrue(prompt.captured.currentInput.contains("기기 캘린더를 읽지 못했습니다"))
    }

    @Test
    fun `상한 초과 시 에피소드부터 뒤에서 떨어져 나간다`() = runBlocking {
        val prompt = slot<ChatPrompt>()
        coEvery { modelRunner.generate(capture(prompt), any()) } returns
            AppResult.Success(ModelTurn("브리핑"))
        // 에피소드 요약 하나가 약 1000토큰(2000자/2) — 3개면 상한(1400)을 확실히 넘는다.
        val fat = (1..3).map { episode("기억$it", "가".repeat(2000)) }

        useCase()(materials(episodes = fat))

        val input = prompt.captured.currentInput
        assertTrue("일정(뼈대)은 보존돼야 한다", input.contains("특강"))
        assertTrue("마지막 에피소드는 잘려야 한다", !input.contains("기억3"))
    }

    @Test
    fun `생성 실패는 그대로 전파된다`() = runBlocking {
        coEvery { modelRunner.generate(any(), any()) } returns
            AppResult.Failure(com.kosmos.app.core.common.AppError.ModelInferenceError("x"))

        val result = useCase()(materials())

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `빈 출력은 실패로 올라간다`() = runBlocking {
        // [WHY] 빈 브리핑을 성공으로 내리면 오늘 생성됨으로 기록되어 그날 브리핑이 영영 없다.
        coEvery { modelRunner.generate(any(), any()) } returns
            AppResult.Success(ModelTurn("  "))

        val result = useCase()(materials())

        assertTrue(result is AppResult.Failure)
    }
}
