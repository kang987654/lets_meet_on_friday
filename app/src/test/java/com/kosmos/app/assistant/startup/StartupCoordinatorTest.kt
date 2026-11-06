package com.kosmos.app.assistant.startup

import com.kosmos.app.assistant.briefing.MorningBriefingGenerator
import com.kosmos.app.assistant.episode.EpisodeSummarizeScheduler
import com.kosmos.app.assistant.orchestrator.ConversationPrewarmer
import com.kosmos.app.core.common.AppError
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.startup.StartupStage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [StartupCoordinatorTest]
 * 엔진 Ready 뒤 시작 단계(0.41.7 M2) — 지난 대화 정리 → 오늘 브리핑 → 대화 미리 준비를 순서대로, 끝나면 catch-up 요청.
 * 각자 Ready 를 구독하던 세 테스트(ConversationPrewarmerTest·MorningBriefingGeneratorTest·EpisodeSummarizeSchedulerTest)의
 * "Ready 뒤에 이 일이 일어난다"를 여기로 옮겼다(사용자 승인 2026-11-06 — 트리거만 바뀌고 동작은 같다).
 */
class StartupCoordinatorTest {

    private val loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Loading)
    private val runner: ModelRunner = mockk<ModelRunner>(relaxed = true).also {
        every { it.loadState } returns (loadState as StateFlow<ModelLoadState>)
    }
    private val stagesSeen = CopyOnWriteArrayList<StartupStage>()
    private lateinit var coordinator: StartupCoordinator

    private val scheduler: EpisodeSummarizeScheduler = mockk(relaxed = true) {
        coEvery { summarizeLatestStale(any(), any()) } coAnswers {
            secondArg<() -> Unit>().invoke()
            stagesSeen += coordinator.stage.value
            true
        }
    }
    private val briefing: MorningBriefingGenerator = mockk(relaxed = true) {
        coEvery { maybeGenerate(any(), any()) } coAnswers {
            secondArg<() -> Unit>().invoke()
            stagesSeen += coordinator.stage.value
        }
    }
    private val prepared = CopyOnWriteArrayList<String>()
    private val prewarmer: ConversationPrewarmer = mockk(relaxed = true) {
        coEvery { prepareNow(any()) } coAnswers {
            stagesSeen += coordinator.stage.value
            prepared += firstArg<String>()
        }
    }
    private val sessionStore: SessionStore = mockk(relaxed = true) {
        every { activeSessionIdFlow } returns flowOf("s1")
    }

    private fun coordinator() = StartupCoordinator(runner, scheduler, briefing, prewarmer, sessionStore).also { coordinator = it }

    private suspend fun awaitStage(stage: StartupStage) {
        withTimeout(5_000) { while (coordinator.stage.value != stage) delay(10) }
    }

    @Test
    fun `엔진이 준비되면 정리·브리핑·대화 준비를 차례로 하고 끝나면 catch-up 을 맡긴다`() = runBlocking {
        coordinator().start()
        awaitStage(StartupStage.WAITING_ENGINE)

        loadState.value = ModelLoadState.Ready(mockk(relaxed = true))
        awaitStage(StartupStage.DONE)

        assertEquals(listOf(StartupStage.SUMMARIZING, StartupStage.BRIEFING, StartupStage.PREPARING), stagesSeen.toList())
        assertEquals(listOf("s1"), prepared.toList())
        coVerifyOrder {
            scheduler.summarizeLatestStale(any(), any())
            briefing.maybeGenerate(any(), any())
            prewarmer.prepareNow("s1")
            scheduler.requestCatchUp()
        }
    }

    @Test
    fun `복귀로 다시 Ready 가 되면 매번 다시 준비한다`() = runBlocking {
        coordinator().start()
        loadState.value = ModelLoadState.Ready(mockk(relaxed = true))
        withTimeout(5_000) { while (prepared.size < 1) delay(10) }

        // 백그라운드 해제 후 복귀
        loadState.value = ModelLoadState.Loading
        loadState.value = ModelLoadState.Ready(mockk(relaxed = true))
        withTimeout(5_000) { while (prepared.size < 2) delay(10) }
        awaitStage(StartupStage.DONE)

        assertEquals(2, prepared.size)
        coVerify(exactly = 2) { briefing.maybeGenerate(any(), any()) }
    }

    @Test
    fun `정리 중에 사용자가 요청을 보내면 남은 단계를 건너뛴다`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        coEvery { scheduler.summarizeLatestStale(any(), any()) } coAnswers {
            secondArg<() -> Unit>().invoke()
            gate.await()
            true
        }
        coordinator().start()
        loadState.value = ModelLoadState.Ready(mockk(relaxed = true))
        awaitStage(StartupStage.SUMMARIZING)

        coordinator.onUserActivity()
        assertEquals("요청을 보내면 단계 표시가 바로 끝난다", StartupStage.DONE, coordinator.stage.value)
        gate.complete(Unit)
        withTimeout(5_000) { while (runCatching { verify { scheduler.requestCatchUp() } }.isFailure) delay(10) }

        coVerify(exactly = 0) { briefing.maybeGenerate(any(), any()) }
        coVerify(exactly = 0) { prewarmer.prepareNow(any()) }
        assertEquals(StartupStage.DONE, coordinator.stage.value)
    }

    @Test
    fun `할 일이 없는 단계는 문구를 띄우지 않는다`() = runBlocking {
        coEvery { scheduler.summarizeLatestStale(any(), any()) } returns false
        coEvery { briefing.maybeGenerate(any(), any()) } returns Unit
        coordinator().start()

        loadState.value = ModelLoadState.Ready(mockk(relaxed = true))
        awaitStage(StartupStage.DONE)

        assertEquals(listOf(StartupStage.PREPARING), stagesSeen.toList())
    }

    @Test
    fun `엔진 오류면 시작 단계가 돌지 않는다`() = runBlocking {
        coordinator().start()
        loadState.value = ModelLoadState.Error(AppError.ModelNotReady("x"))
        withTimeout(5_000) { while (coordinator.stage.value != StartupStage.IDLE) delay(10) }

        assertEquals(StartupStage.IDLE, coordinator.stage.value)
        coVerify(exactly = 0) { prewarmer.prepareNow(any()) }
    }
}
