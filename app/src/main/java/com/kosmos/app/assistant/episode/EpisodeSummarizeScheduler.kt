package com.kosmos.app.assistant.episode

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.usecase.SummarizeEpisodeUseCase
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [EpisodeSummarizeScheduler]
 * 닫힌 에피소드의 요약을 **사용자 턴과 경합하지 않는 시점**에 실행합니다 (ADR-022).
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Episode)
 * - **Dependencies**: [EpisodeBoundaryManager], [SummarizeEpisodeUseCase], [RuntimeMetricsCollector]
 *
 * ### Key Flow
 * 1. [EpisodeBoundaryManager.closedEpisodes] 를 구독해 지연 큐에 쌓는다.
 * 2. [onTurnCompleted] (오케스트레이터가 턴 종료 후 호출) 와 모델 Ready 시점에 큐를 비운다.
 * 3. 모델 Ready 시 catch-up — 강제종료로 못 닫은 OPEN·미배정 메시지·미요약 CLOSED 복구.
 *
 * [WHY] **계획의 "무활동 닫힘 = 즉시 실행"을 버리고 전부 턴 종료 후로 미룬다.** 무활동 경계는
 * 새 유저 메시지가 도착해야 감지되는데, 그 시점에 요약(oneShot 추론)을 바로 던지면
 * llmDispatcher(limitedParallelism(1))에서 **사용자 턴보다 먼저 줄을 서** 응답을 수십 초
 * 지연시킬 수 있다. 턴 종료 후 드레인이면 경합이 구조적으로 없다 — 사용자가 응답을 읽는 동안
 * 요약이 돈다.
 *
 * [WHY] **"ON_STOP 시 소비"도 버렸다** — KosmosApp 의 onStop 은 modelRunner.close() 로 엔진을
 * 해제한다(0.16.2 메모리 반환 정책). 그 시점의 요약은 해제와 경쟁하거나 3.6GB 재로드를
 * 유발한다. 백그라운드로 넘어간 미요약분은 다음 Ready 의 catch-up 이 DB status 로 복원한다 —
 * 큐는 캐시일 뿐 DB 가 진실이다.
 */
@Singleton
class EpisodeSummarizeScheduler @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val conversationRepository: ConversationRepository,
    private val summarizeEpisode: SummarizeEpisodeUseCase,
    private val metricsCollector: RuntimeMetricsCollector,
    boundaryManager: EpisodeBoundaryManager,
    modelRunner: ModelRunner
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // [WHY] 드레인과 catch-up 을 직렬화한다 — Ready 와 턴 종료가 겹치면 같은 에피소드를
    // 두 번 요약할 수 있다 (요약 = 추론 1회라 중복이 비싸다).
    private val drainMutex = Mutex()
    private val deferred = ArrayDeque<String>()

    init {
        scope.launch {
            boundaryManager.closedEpisodes.collect { closed ->
                synchronized(deferred) { deferred.addLast(closed.episodeId) }
            }
        }
        scope.launch {
            modelRunner.loadState.collect { state ->
                if (state is ModelLoadState.Ready) {
                    runCatching { catchUp() }
                        .onFailure { AppLogger.e(TAG, "catch-up 실패", it) }
                    drain()
                }
            }
        }
    }

    /** 오케스트레이터가 턴 종료 후 호출 — 지연 큐를 백그라운드로 비운다 (fire-and-forget). */
    fun onTurnCompleted() {
        scope.launch { drain() }
    }

    private suspend fun drain() = drainMutex.withLock {
        while (true) {
            val id = synchronized(deferred) { deferred.removeFirstOrNull() } ?: break
            process(id)
        }
    }

    private suspend fun process(episodeId: String) {
        // [WHY] 발열 게이트 — 경고 온도(43°C) 이상이면 미룬다. 요약은 급하지 않은 추론이고,
        // 다음 드레인(턴 종료/Ready)에서 재시도된다.
        if (metricsCollector.getCurrentTemp() >= Constants.THERMAL_WARNING_CELSIUS) {
            synchronized(deferred) { deferred.addLast(episodeId) }
            return
        }

        val episode = (episodeRepository.getById(episodeId) as? AppResult.Success)?.data ?: return
        if (episode.status != EpisodeStatus.CLOSED) return // 이미 처리됐거나 종결

        val messages = (conversationRepository.getByEpisode(episodeId) as? AppResult.Success)
            ?.data.orEmpty()
        if (messages.isEmpty()) {
            // [WHY] 내용 없는 에피소드(경계 직후 강제종료 등)는 요약할 것이 없다 — 남겨 두면
            // catch-up 이 영원히 재시도하므로 지운다. 원문 메시지가 없으니 잃는 것도 없다.
            episodeRepository.delete(episodeId)
            return
        }

        when (val result = summarizeEpisode(messages)) {
            is AppResult.Success -> applyDocs(episode, result.data)
            is AppResult.Failure -> {
                val retried = episode.copy(
                    retryCount = episode.retryCount + 1,
                    // [WHY] 상한 도달 시 FAILED 고정 — 아카이브에 노출되지 않지만 원문은
                    // 타임라인에 그대로다(요약 실패 ≠ 데이터 손실).
                    status = if (episode.retryCount + 1 >= MAX_RETRY) EpisodeStatus.FAILED
                    else EpisodeStatus.CLOSED,
                    updatedAt = System.currentTimeMillis()
                )
                episodeRepository.update(retried)
                AppLogger.w(TAG, "에피소드 요약 실패(${retried.retryCount}/${MAX_RETRY}): ${result.error}")
            }
        }
    }

    /**
     * 요약 문서를 반영합니다. 다중 주제면 첫 문서가 기존 행을, 나머지가 추가 행을 차지한다
     * (메시지 episodeId 는 그대로 — 원문 이동은 시간 범위가 같으므로 충분하다, M0 게이트).
     */
    private suspend fun applyDocs(episode: Episode, docs: List<SummarizeEpisodeUseCase.EpisodeDoc>) {
        val now = System.currentTimeMillis()
        docs.forEachIndexed { index, doc ->
            val row = if (index == 0) {
                episode.copy(
                    status = EpisodeStatus.SUMMARIZED,
                    title = doc.title,
                    summary = doc.summary,
                    tags = doc.tags,
                    updatedAt = now
                )
            } else {
                episode.copy(
                    id = UUID.randomUUID().toString(),
                    status = EpisodeStatus.SUMMARIZED,
                    title = doc.title,
                    summary = doc.summary,
                    tags = doc.tags,
                    createdAt = now,
                    updatedAt = now
                )
            }
            if (index == 0) episodeRepository.update(row) else episodeRepository.insert(row)
        }
    }

    /**
     * 강제종료 복구 — 앱 시작 후 모델 Ready 시 1회씩.
     * ① 마지막 메시지가 30분 이상 과거인 OPEN → CLOSED
     * ② 미배정(episodeId NULL) 메시지 → 시간 간격으로 소급 에피소드 생성·배정
     * ③ 미요약 CLOSED → 큐 투입 (drain 이 이어서 처리)
     */
    internal suspend fun catchUp() {
        val now = System.currentTimeMillis()

        // ① 낡은 OPEN 닫기
        val open = (episodeRepository.getByStatus(EpisodeStatus.OPEN) as? AppResult.Success)
            ?.data.orEmpty()
        for (ep in open) {
            val messages = (conversationRepository.getByEpisode(ep.id) as? AppResult.Success)
                ?.data.orEmpty()
            val lastAt = messages.lastOrNull()?.createdAt
            when {
                messages.isEmpty() && now - ep.startAt > EpisodeBoundaryManager.IDLE_BOUNDARY_MS ->
                    episodeRepository.delete(ep.id)
                lastAt != null && now - lastAt > EpisodeBoundaryManager.IDLE_BOUNDARY_MS ->
                    episodeRepository.update(
                        ep.copy(
                            status = EpisodeStatus.CLOSED,
                            endAt = lastAt,
                            messageCount = messages.size,
                            updatedAt = now
                        )
                    )
            }
        }

        // ② 미배정 메시지 소급 배정 — 최근 30분 이내 분은 건드리지 않는다 (지금 열리는
        //    에피소드에 배정되는 중일 수 있는 불안정 구간).
        val unassigned = (conversationRepository.getUnassigned() as? AppResult.Success)
            ?.data.orEmpty()
            .filter { now - it.createdAt > EpisodeBoundaryManager.IDLE_BOUNDARY_MS }
        if (unassigned.isNotEmpty()) backfill(unassigned, now)

        // ③ 미요약 CLOSED 큐 투입
        val closed = (episodeRepository.getByStatus(EpisodeStatus.CLOSED) as? AppResult.Success)
            ?.data.orEmpty()
        synchronized(deferred) {
            closed.forEach { if (it.id !in deferred) deferred.addLast(it.id) }
        }
    }

    /** 시간 간격(30분)으로 잘라 소급 에피소드를 만들고 메시지를 배정한다 (exp33 분절 규칙). */
    private suspend fun backfill(messages: List<ChatMessage>, now: Long) {
        var chunk = mutableListOf<ChatMessage>()
        suspend fun flush() {
            if (chunk.isEmpty()) return
            val ep = Episode(
                id = UUID.randomUUID().toString(),
                sessionId = chunk.first().sessionId,
                status = EpisodeStatus.CLOSED,
                title = null, summary = null, tags = emptyList(),
                startAt = chunk.first().createdAt,
                endAt = chunk.last().createdAt,
                messageCount = chunk.size,
                retryCount = 0,
                createdAt = now, updatedAt = now
            )
            episodeRepository.insert(ep)
            chunk.forEach { conversationRepository.assignEpisode(it.id, ep.id) }
            synchronized(deferred) { deferred.addLast(ep.id) }
            chunk = mutableListOf()
        }
        var prev: Long? = null
        for (m in messages) {
            if (prev != null && m.createdAt - prev > EpisodeBoundaryManager.IDLE_BOUNDARY_MS) flush()
            chunk.add(m)
            prev = m.createdAt
        }
        flush()
    }

    private companion object {
        const val TAG = "EpisodeScheduler"
        const val MAX_RETRY = 3
    }
}
