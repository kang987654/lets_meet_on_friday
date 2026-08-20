package com.kosmos.app.assistant.briefing

import com.kosmos.app.assistant.episode.EpisodeBoundaryManager
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.usecase.GenerateBriefingUseCase
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * [MorningBriefingGeneratorTest]
 * 브리핑 생성의 실행 조건(설정·시각·중복·발열)과 저장 계약을 못박습니다 (A4 하이브리드).
 *
 * [WHY] 판정 시각은 [MorningBriefingGenerator.maybeGenerate] 의 now 인자로 주입한다 —
 * 벽시계에 묶으면 테스트가 실행 시각(설정 시각 전/후)에 따라 갈리는 플레이크가 된다.
 */
class MorningBriefingGeneratorTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 고정 판정 시각 — 2026-08-21 10:00 (기기 시간대). 기본 설정 09:17 이후다. */
    private val now: Long =
        LocalDate.of(2026, 8, 21).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    private val settingsDataStore: SettingsDataStore = mockk()
    private val sessionStore: SessionStore = mockk(relaxed = true)
    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val episodeRepository: EpisodeRepository = mockk(relaxed = true)
    private val taskRepository: TaskRepository = mockk(relaxed = true)
    private val getTodaySchedule: GetTodayScheduleUseCase = mockk()
    private val generateBriefing: GenerateBriefingUseCase = mockk()
    private val boundaryManager: EpisodeBoundaryManager = mockk()
    private val auditTrailService: AuditTrailService = mockk(relaxed = true)
    private val metricsCollector: RuntimeMetricsCollector = mockk()
    private val loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Loading)
    private val modelRunner: ModelRunner = mockk(relaxed = true) {
        io.mockk.every { this@mockk.loadState } returns this@MorningBriefingGeneratorTest.loadState
    }

    @Before
    fun defaults() {
        io.mockk.every { settingsDataStore.briefingEnabledFlow } returns flowOf(true)
        io.mockk.every { settingsDataStore.briefingTimeMinutesFlow } returns flowOf(9 * 60 + 17)
        io.mockk.every { sessionStore.activeSessionIdFlow } returns flowOf("s1")
        io.mockk.every { metricsCollector.getCurrentTemp() } returns 25f
        coEvery { conversationRepository.countByInputTypeSince(InputType.BRIEFING, any()) } returns
            AppResult.Success(0)
        coEvery { conversationRepository.save(any()) } returns AppResult.Success(Unit)
        coEvery { getTodaySchedule(ScheduleData.RangeType.TODAY) } returns AppResult.Success(
            ScheduleData(events = persistentListOf(), summary = null, rangeType = ScheduleData.RangeType.TODAY)
        )
        coEvery { taskRepository.getPendingTasksData(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { episodeRepository.getEpisodes(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { generateBriefing(any()) } returns AppResult.Success("좋은 아침이에요! 오늘은…")
        coEvery { boundaryManager.onAssistantInitiatedMessage(any(), any()) } returns "ep-1"
    }

    private fun generator() = MorningBriefingGenerator(
        settingsDataStore, sessionStore, conversationRepository, episodeRepository,
        taskRepository, getTodaySchedule, generateBriefing, boundaryManager,
        auditTrailService, metricsCollector, modelRunner
    )

    @Test
    fun `성공 경로 - BRIEFING 메시지가 에피소드 배정과 함께 저장되고 감사·신호가 남는다`() = runBlocking {
        val saved = slot<ChatMessage>()
        coEvery { conversationRepository.save(capture(saved)) } returns AppResult.Success(Unit)
        val g = generator()

        g.maybeGenerate(now)

        assertEquals(ChatMessage.Role.ASSISTANT, saved.captured.role)
        assertEquals(InputType.BRIEFING, saved.captured.inputType)
        assertEquals("ep-1", saved.captured.episodeId)
        assertEquals("s1", saved.captured.sessionId)
        coVerify { auditTrailService.logModelRun(GenerateBriefingUseCase.SESSION_ID, any(), any()) }
        assertTrue("저장 신호가 방출돼야 화면이 재동기화된다", g.briefingSaved.replayCache.isNotEmpty())
    }

    @Test
    fun `꺼져 있으면 생성하지 않는다`() = runBlocking {
        io.mockk.every { settingsDataStore.briefingEnabledFlow } returns flowOf(false)

        generator().maybeGenerate(now)

        coVerify(exactly = 0) { generateBriefing(any()) }
    }

    @Test
    fun `설정 시각 전이면 생성하지 않는다`() = runBlocking {
        // 판정 시각(10:00)보다 뒤인 11:40 으로 설정 — 아직 아침 전이다.
        io.mockk.every { settingsDataStore.briefingTimeMinutesFlow } returns flowOf(11 * 60 + 40)

        generator().maybeGenerate(now)

        coVerify(exactly = 0) { generateBriefing(any()) }
    }

    @Test
    fun `오늘 이미 생성했으면 다시 생성하지 않는다`() = runBlocking {
        coEvery { conversationRepository.countByInputTypeSince(InputType.BRIEFING, any()) } returns
            AppResult.Success(1)

        generator().maybeGenerate(now)

        coVerify(exactly = 0) { generateBriefing(any()) }
    }

    @Test
    fun `판정 조회가 실패하면 중복 위험이므로 생성하지 않는다`() = runBlocking {
        coEvery { conversationRepository.countByInputTypeSince(InputType.BRIEFING, any()) } returns
            AppResult.Failure(com.kosmos.app.core.common.AppError.DbReadError("conversation"))

        generator().maybeGenerate(now)

        coVerify(exactly = 0) { generateBriefing(any()) }
    }

    @Test
    fun `발열 경고면 미루고 생성하지 않는다`() = runBlocking {
        io.mockk.every { metricsCollector.getCurrentTemp() } returns 44f

        generator().maybeGenerate(now)

        coVerify(exactly = 0) { generateBriefing(any()) }
    }

    @Test
    fun `일정 조회 실패는 기기 캘린더 미확인으로 브리핑에 전달된다`() = runBlocking {
        // [WHY] 조회 실패를 조용히 삼키면 "오늘 일정 없음"이 거짓이 된다 (EC4).
        coEvery { getTodaySchedule(any()) } returns
            AppResult.Failure(com.kosmos.app.core.common.AppError.CalendarReadError("x"))
        val materials = slot<GenerateBriefingUseCase.BriefingMaterials>()
        coEvery { generateBriefing(capture(materials)) } returns AppResult.Success("브리핑")

        generator().maybeGenerate(now)

        assertTrue(materials.captured.deviceCalendarFailed)
    }

    @Test
    fun `추론 실패면 저장하지 않는다 - 다음 Ready 가 자연 재시도한다`() = runBlocking {
        coEvery { generateBriefing(any()) } returns
            AppResult.Failure(com.kosmos.app.core.common.AppError.ModelInferenceError("x"))

        generator().maybeGenerate(now)

        coVerify(exactly = 0) { conversationRepository.save(any()) }
    }

    @Test
    fun `Ready 전이에 편승해 생성한다`() = runBlocking {
        // [WHY] 시각 0분(자정)으로 두면 어떤 벽시계 now 도 통과한다 — start() 경로는 실제
        // 시각을 쓰므로 이 테스트만 시각 조건을 무력화해 결정적으로 만든다.
        io.mockk.every { settingsDataStore.briefingTimeMinutesFlow } returns flowOf(0)
        val g = generator()
        g.start()

        loadState.value = ModelLoadState.Ready(mockk(relaxed = true))
        waitUntil { g.briefingSaved.replayCache.isNotEmpty() }

        coVerify(exactly = 1) { generateBriefing(any()) }
    }

    private suspend fun waitUntil(timeoutMs: Long = 2_000, cond: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!cond() && System.currentTimeMillis() - start < timeoutMs) delay(20)
    }
}
