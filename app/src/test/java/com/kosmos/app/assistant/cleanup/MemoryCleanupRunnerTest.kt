package com.kosmos.app.assistant.cleanup

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.cleanup.ApplyMemoryMergeUseCase
import com.kosmos.app.domain.cleanup.GenerateWeeklyReviewUseCase
import com.kosmos.app.domain.cleanup.MergeProposal
import com.kosmos.app.domain.cleanup.PlanMemoryMergeUseCase
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.modelrunner.ModelInfo
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MemoryCleanupRunnerTest]
 * 수동 정리 실행기(0.30.0 M2) — 발열·엔진 게이트, 자동 요약과의 잠금 공유, 취소 시 무적용, 체크한 제안만 적용.
 */
class MemoryCleanupRunnerTest {

    private val gate = BackgroundInferenceGate()
    private val review: GenerateWeeklyReviewUseCase = mockk()
    private val plan: PlanMemoryMergeUseCase = mockk()
    private val apply: ApplyMemoryMergeUseCase = mockk()
    private val metrics: RuntimeMetricsCollector = mockk { every { getCurrentTemp() } returns 30f }
    private val loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Ready(ModelInfo("m", "m", "1", "Q4", 0L)))
    private val modelRunner: ModelRunner = mockk { every { loadState } returns this@MemoryCleanupRunnerTest.loadState }

    private val reviewNote = KnowledgeNote(id = "weekly-review-2026-W40", content = "이번 주 회고", createdAt = 0L, updatedAt = 0L)
    private val proposal = MergeProposal(
        listOf(
            KnowledgeNote(id = "a", content = "x 1", createdAt = 0L, updatedAt = 0L),
            KnowledgeNote(id = "b", content = "x 1", createdAt = 0L, updatedAt = 0L)
        ),
        "x 1"
    )

    // 0.31.0 생성부 추가 — 에피소드 통합은 이 테스트의 관심사가 아니다(제안 없음).
    private val planEpisodes: com.kosmos.app.domain.cleanup.PlanEpisodeMergeUseCase = mockk {
        coEvery { this@mockk.invoke(any(), any()) } returns AppResult.Success(emptyList())
    }
    private val applyEpisodes: com.kosmos.app.domain.cleanup.ApplyEpisodeMergeUseCase = mockk(relaxed = true)

    private fun runner() = MemoryCleanupRunner(gate, review, plan, apply, planEpisodes, applyEpisodes, metrics, modelRunner)

    private fun givenHappyPath() {
        coEvery { review(any(), any()) } returns AppResult.Success(reviewNote)
        coEvery { plan(any()) } returns AppResult.Success(listOf(proposal))
    }

    private fun awaitState(r: MemoryCleanupRunner, predicate: (MemoryCleanupRunner.State) -> Boolean) = runBlocking {
        withTimeout(3_000) { r.state.first(predicate) }
    }

    @Test
    fun `기기가 뜨거우면 시작하지 않는다`() {
        every { metrics.getCurrentTemp() } returns 50f
        val r = runner()

        r.start()

        assertTrue(r.state.value is MemoryCleanupRunner.State.Blocked)
        coVerify(exactly = 0) { review(any(), any()) }
    }

    @Test
    fun `엔진이 준비되지 않았으면 시작하지 않는다`() {
        loadState.value = ModelLoadState.Loading
        val r = runner()

        r.start()

        assertEquals("AI 모델이 준비되면 다시 시도해 주세요.", (r.state.value as MemoryCleanupRunner.State.Blocked).reason)
    }

    @Test
    fun `회고와 병합 제안을 만들어 검토 상태로 멈춘다`() {
        givenHappyPath()
        val r = runner()

        r.start()

        val review = awaitState(r) { it is MemoryCleanupRunner.State.Review } as MemoryCleanupRunner.State.Review
        assertEquals(reviewNote, review.weeklyReview)
        assertEquals(listOf(proposal), review.proposals)
        coVerify(exactly = 0) { apply(any(), any()) }
    }

    @Test
    fun `자동 요약이 잠금을 쥐고 있으면 끝날 때까지 기다린다`() = runBlocking {
        givenHappyPath()
        gate.mutex.lock()
        val r = runner()

        r.start()
        kotlinx.coroutines.delay(200)
        assertEquals(MemoryCleanupRunner.State.Waiting, r.state.value)
        coVerify(exactly = 0) { review(any(), any()) }

        gate.mutex.unlock()
        awaitState(r) { it is MemoryCleanupRunner.State.Review }
        Unit
    }

    @Test
    fun `취소하면 처음 상태로 돌아가고 아무것도 적용하지 않는다`() {
        val gateOpen = CompletableDeferred<Unit>()
        coEvery { review(any(), any()) } coAnswers { gateOpen.await(); AppResult.Success(reviewNote) }
        val r = runner()

        r.start()
        awaitState(r) { it is MemoryCleanupRunner.State.Running }
        r.cancel()

        assertEquals(MemoryCleanupRunner.State.Idle, r.state.value)
        coVerify(exactly = 0) { plan(any()) }
        coVerify(exactly = 0) { apply(any(), any()) }
    }

    @Test
    fun `체크한 제안만 적용하고 결과를 남긴다`() = runBlocking {
        givenHappyPath()
        coEvery { apply(listOf(proposal), any()) } returns 1
        val r = runner()
        r.start()
        awaitState(r) { it is MemoryCleanupRunner.State.Review }

        r.apply(listOf(proposal))

        assertEquals(MemoryCleanupRunner.State.Done(reviewNote, 1), r.state.value)
    }

    @Test
    fun `아무것도 체크하지 않으면 적용 유스케이스를 부르지 않는다`() = runBlocking {
        givenHappyPath()
        val r = runner()
        r.start()
        awaitState(r) { it is MemoryCleanupRunner.State.Review }

        r.apply(emptyList())

        assertEquals(MemoryCleanupRunner.State.Done(reviewNote, 0), r.state.value)
        coVerify(exactly = 0) { apply(any(), any()) }
    }

    // --- 대화 통합 (0.31.0) ---

    private fun episode(id: String) = com.kosmos.app.domain.model.Episode(
        id = id, sessionId = "s", status = com.kosmos.app.domain.model.EpisodeStatus.SUMMARIZED, title = "제목$id",
        summary = "요약", tags = emptyList(), startAt = 0L, endAt = 1L, messageCount = 2, retryCount = 0, createdAt = 0L, updatedAt = 0L
    )

    @Test
    fun `대화 통합 제안을 검토 상태에 싣고 적용 결과에 합친 수와 건너뛴 수를 남긴다`() = runBlocking {
        givenHappyPath()
        val p1 = com.kosmos.app.domain.cleanup.EpisodeMergeProposal(listOf(episode("a"), episode("b")))
        val p2 = com.kosmos.app.domain.cleanup.EpisodeMergeProposal(listOf(episode("c"), episode("d")))
        coEvery { planEpisodes(any(), any()) } returns AppResult.Success(listOf(p1, p2))
        coEvery { applyEpisodes(listOf(p1, p2), any()) } returns 1
        val r = runner()
        r.start()

        val review = awaitState(r) { it is MemoryCleanupRunner.State.Review } as MemoryCleanupRunner.State.Review
        assertEquals(listOf(p1, p2), review.episodeProposals)

        r.apply(emptyList(), listOf(p1, p2))

        assertEquals(MemoryCleanupRunner.State.Done(reviewNote, 0, episodesMerged = 1, episodesSkipped = 1), r.state.value)
    }

    @Test
    fun `대화 통합 적용은 자동 요약과 같은 잠금 안에서 돈다`() = runBlocking {
        givenHappyPath()
        val p1 = com.kosmos.app.domain.cleanup.EpisodeMergeProposal(listOf(episode("a"), episode("b")))
        coEvery { planEpisodes(any(), any()) } returns AppResult.Success(listOf(p1))
        var lockedDuringApply = false
        coEvery { applyEpisodes(any(), any()) } coAnswers { lockedDuringApply = gate.mutex.isLocked; 1 }
        val r = runner()
        r.start()
        awaitState(r) { it is MemoryCleanupRunner.State.Review }

        r.apply(emptyList(), listOf(p1))

        assertTrue(lockedDuringApply)
    }
}
