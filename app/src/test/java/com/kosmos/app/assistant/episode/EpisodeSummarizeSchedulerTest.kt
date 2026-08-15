package com.kosmos.app.assistant.episode

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.usecase.SummarizeEpisodeUseCase
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EpisodeSummarizeSchedulerTest]
 * 요약 실행 시점(턴 종료 드레인·Ready catch-up)·재시도 상한·발열 게이트를 검증합니다.
 *
 * [WHY] 이 스케줄러의 계약은 "언제 돌리는가"다 — 잘못 돌리면 사용자 턴을 수십 초 막거나
 * (llmDispatcher 직렬), 발열 중에 추론을 얹거나, 같은 에피소드를 두 번 요약한다. 실행
 * **시점**을 못박는 것이 요약 내용보다 중요하다.
 */
class EpisodeSummarizeSchedulerTest {

    private val episodeRepository: EpisodeRepository = mockk(relaxed = true)
    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val summarize: SummarizeEpisodeUseCase = mockk()
    private val metricsCollector: RuntimeMetricsCollector = mockk {
        every { getCurrentTemp() } returns 30f
    }
    // [WHY] replay — 테스트의 emit 이 스케줄러 init 의 구독보다 먼저 실행될 수 있다(경합).
    // 실제 앱에서는 boundaryManager 방출이 항상 구독 뒤이므로(둘 다 @Singleton 생성 시점)
    // 프로덕션 코드는 replay 없이 두고 테스트만 보정한다.
    private val closed = MutableSharedFlow<EpisodeBoundaryManager.ClosedEpisode>(replay = 8)
    private val boundaryManager: EpisodeBoundaryManager = mockk {
        every { closedEpisodes } returns (closed as SharedFlow<EpisodeBoundaryManager.ClosedEpisode>)
    }
    private val loadStateFlow = MutableStateFlow<ModelLoadState>(ModelLoadState.Loading)
    private val modelRunner: ModelRunner = mockk<ModelRunner>(relaxed = true).also {
        // [WHY] mockk 초기화 람다 안에서 바깥 프로퍼티가 리시버(모의 객체)의 동명 프로퍼티에
        // 가려져 자기 자신을 반환하도록 기록되는 함정이 있다 — also 로 리시버를 분리한다.
        every { it.loadState } returns (loadStateFlow as StateFlow<ModelLoadState>)
    }

    private val saved = mutableListOf<Episode>()

    @org.junit.Before
    fun setUpDefaults() {
        // [WHY] 기본 스텁은 @Before 에서 — 테스트 본문(나중 실행)의 구체 스텁이 이긴다.
        // 헬퍼 함수 안에서 스텁하면 테스트별 스텁을 도로 덮어쓰는 함정이 있다(실제로 겪음).
        coEvery { episodeRepository.update(any()) } answers {
            saved += firstArg<Episode>(); AppResult.Success(Unit)
        }
        coEvery { episodeRepository.insert(any()) } answers {
            saved += firstArg<Episode>(); AppResult.Success(Unit)
        }
        coEvery { episodeRepository.getByStatus(any()) } returns AppResult.Success(emptyList())
        coEvery { conversationRepository.getUnassigned() } returns AppResult.Success(emptyList())
    }

    private fun scheduler(): EpisodeSummarizeScheduler = EpisodeSummarizeScheduler(
        episodeRepository, conversationRepository, summarize,
        metricsCollector, boundaryManager, modelRunner
    )

    private fun closedEpisode(id: String = "e1") = Episode(
        id = id, sessionId = "s1", status = EpisodeStatus.CLOSED, title = null, summary = null,
        tags = emptyList(), startAt = 100, endAt = 200, messageCount = 2, retryCount = 0,
        createdAt = 100, updatedAt = 200
    )

    private fun messages() = listOf(
        ChatMessage("m1", "s1", ChatMessage.Role.USER, "자전거 비밀번호 1234", InputType.TEXT, createdAt = 100),
        ChatMessage("m2", "s1", ChatMessage.Role.ASSISTANT, "기억했어요", InputType.TEXT, createdAt = 200)
    )

    private fun doc(title: String = "자전거 메모") =
        SummarizeEpisodeUseCase.EpisodeDoc(title, listOf("자전거"), "비밀번호를 저장했다")

    private suspend fun waitUntil(timeoutMs: Long = 2_000, cond: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!cond() && System.currentTimeMillis() - start < timeoutMs) delay(20)
    }

    @Test
    fun `닫힘 이벤트만으로는 요약이 돌지 않고 턴 종료 드레인에서 돈다`() = runBlocking {
        coEvery { episodeRepository.getById("e1") } returns AppResult.Success(closedEpisode())
        coEvery { conversationRepository.getByEpisode("e1") } returns AppResult.Success(messages())
        coEvery { summarize(any()) } returns AppResult.Success(listOf(doc()))
        val s = scheduler()

        closed.emit(EpisodeBoundaryManager.ClosedEpisode("e1", EpisodeBoundaryManager.CloseTrigger.IDLE))
        delay(300)
        // [WHY] 여기서 이미 돌았다면 사용자 턴(직후 프리필)과 llmDispatcher 를 다퉜을 것이다.
        assertTrue("턴 종료 전에 요약이 돌았다", saved.none { it.status == EpisodeStatus.SUMMARIZED })

        s.onTurnCompleted()
        waitUntil { saved.any { it.status == EpisodeStatus.SUMMARIZED } }

        assertEquals("자전거 메모", saved.single { it.status == EpisodeStatus.SUMMARIZED }.title)
    }

    @Test
    fun `다중 문서는 첫 문서가 기존 행을, 나머지가 새 행을 차지한다`() = runBlocking {
        coEvery { episodeRepository.getById("e1") } returns AppResult.Success(closedEpisode())
        coEvery { conversationRepository.getByEpisode("e1") } returns AppResult.Success(messages())
        coEvery { summarize(any()) } returns AppResult.Success(listOf(doc("문서A"), doc("문서B")))
        val s = scheduler()

        closed.emit(EpisodeBoundaryManager.ClosedEpisode("e1", EpisodeBoundaryManager.CloseTrigger.RESET))
        // [WHY] emit → 수집자(별도 코루틴)가 deferred 에 넣기까지 비동기다 — 즉시 드레인하면
        // 빈 큐를 비우고 끝난다. 실제 앱에서는 턴(수 초)이 그 간격을 보장한다.
        delay(200)
        s.onTurnCompleted()
        waitUntil { saved.count { it.status == EpisodeStatus.SUMMARIZED } == 2 }

        val docs = saved.filter { it.status == EpisodeStatus.SUMMARIZED }
        assertEquals(setOf("문서A", "문서B"), docs.map { it.title }.toSet())
        assertTrue("첫 문서는 원래 id 를 유지한다", docs.any { it.id == "e1" })
        assertTrue("둘째 문서는 새 id 다", docs.any { it.id != "e1" })
        // [WHY] 시간 범위는 공유 — 원문 이동이 같은 지점으로 가면 충분하다 (M0 게이트 결정).
        assertTrue(docs.all { it.startAt == 100L && it.endAt == 200L })
    }

    @Test
    fun `요약 실패는 재시도 카운트를 올리고 3회에 FAILED 로 고정된다`() = runBlocking {
        var episode = closedEpisode()
        coEvery { episodeRepository.getById("e1") } answers { AppResult.Success(episode) }
        coEvery { conversationRepository.getByEpisode("e1") } returns AppResult.Success(messages())
        coEvery { summarize(any()) } returns AppResult.Failure(AppError.ModelInferenceError("파싱 실패"))
        coEvery { episodeRepository.update(any()) } answers {
            episode = firstArg<Episode>(); saved += episode; AppResult.Success(Unit)
        }
        val s = scheduler()

        repeat(3) {
            closed.emit(EpisodeBoundaryManager.ClosedEpisode("e1", EpisodeBoundaryManager.CloseTrigger.IDLE))
            delay(200) // emit → 수집자 정착 (위 다중 문서 테스트와 같은 이유)
            s.onTurnCompleted()
            waitUntil { saved.size >= it + 1 }
        }

        assertEquals(3, episode.retryCount)
        assertEquals(EpisodeStatus.FAILED, episode.status)
        // [WHY] FAILED 는 종결 — 다시 드레인해도 요약을 시도하지 않는다.
        val before = saved.size
        closed.emit(EpisodeBoundaryManager.ClosedEpisode("e1", EpisodeBoundaryManager.CloseTrigger.IDLE))
        s.onTurnCompleted()
        delay(300)
        assertEquals(before, saved.size)
    }

    @Test
    fun `발열 경고 이상이면 요약을 미룬다`() = runBlocking {
        every { metricsCollector.getCurrentTemp() } returns 44f
        coEvery { episodeRepository.getById("e1") } returns AppResult.Success(closedEpisode())
        coEvery { conversationRepository.getByEpisode("e1") } returns AppResult.Success(messages())
        coEvery { summarize(any()) } returns AppResult.Success(listOf(doc()))
        val s = scheduler()

        closed.emit(EpisodeBoundaryManager.ClosedEpisode("e1", EpisodeBoundaryManager.CloseTrigger.IDLE))
        s.onTurnCompleted()
        delay(300)
        assertTrue("발열 중 요약이 돌았다", saved.isEmpty())

        // 식은 뒤 드레인하면 처리된다.
        every { metricsCollector.getCurrentTemp() } returns 35f
        delay(100)
        s.onTurnCompleted()
        waitUntil { saved.any { it.status == EpisodeStatus.SUMMARIZED } }
        assertTrue(saved.any { it.status == EpisodeStatus.SUMMARIZED })
    }

    @Test
    fun `catch-up 은 낡은 OPEN 을 닫고 미배정 메시지를 소급 배정한다`() = runBlocking {
        val now = System.currentTimeMillis()
        val staleOpen = closedEpisode("open1").copy(status = EpisodeStatus.OPEN, endAt = null)
        coEvery { episodeRepository.getByStatus(EpisodeStatus.OPEN) } returns
            AppResult.Success(listOf(staleOpen))
        coEvery { episodeRepository.getByStatus(EpisodeStatus.CLOSED) } returns
            AppResult.Success(emptyList())
        val oldTs = now - 2 * 60 * 60 * 1000L // 2시간 전
        coEvery { conversationRepository.getByEpisode("open1") } returns
            AppResult.Success(listOf(messages()[0].copy(createdAt = oldTs)))
        val orphan1 = messages()[0].copy(id = "o1", createdAt = oldTs)
        val orphan2 = messages()[1].copy(id = "o2", createdAt = oldTs + 60 * 60 * 1000L) // 1시간 뒤 = 별도 에피소드
        coEvery { conversationRepository.getUnassigned() } returns AppResult.Success(listOf(orphan1, orphan2))
        val assigned = mutableListOf<Pair<String, String>>()
        coEvery { conversationRepository.assignEpisode(any(), any()) } answers {
            assigned += firstArg<String>() to secondArg<String>(); AppResult.Success(Unit)
        }
        val s = scheduler()

        s.catchUp()

        assertTrue("낡은 OPEN 이 닫혀야 한다", saved.any { it.id == "open1" && it.status == EpisodeStatus.CLOSED })
        // [WHY] 1시간 간격의 고아 둘은 서로 다른 소급 에피소드를 받아야 한다 (exp33 분절 규칙).
        assertEquals(2, assigned.size)
        assertTrue(assigned[0].second != assigned[1].second)
    }
}
