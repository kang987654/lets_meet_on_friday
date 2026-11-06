package com.kosmos.app.assistant.episode

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.runCatchingCancellable
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.deferRestore
import com.kosmos.app.domain.usecase.SummarizeEpisodeUseCase
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import com.kosmos.app.runtime.metrics.shouldDeferBackgroundInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.kosmos.app.assistant.cleanup.BackgroundInferenceGate
import com.kosmos.app.core.common.AppError
import com.kosmos.app.domain.input.InputActivity
import com.kosmos.app.platform.memory.MemoryEmbedder

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
 * 2. [onTurnCompleted] (오케스트레이터가 턴 종료 후 호출) 와 시작 단계가 끝난 뒤([requestCatchUp]), **조용해지면**([SummaryQuietDelay] — 그 사이
 *    [onUserActivity] 가 오면 다시 센다) 큐를 비운다(0.39.1).
 * 3. 시작 단계([com.kosmos.app.assistant.startup.StartupCoordinator])가 엔진 Ready 뒤 [summarizeLatestStale] 로 **가장 최근** 지난 에피소드 하나를
 *    바로 요약하고(채팅을 열기 전, 진행 문구를 보이며 — 0.41.7 M2), 끝나면 [requestCatchUp] — 강제종료로 못 닫은 OPEN·미배정 메시지·
 *    남은 미요약 CLOSED·미뤄 둔 사실 추출을 조용해진 뒤 복구.
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
    private val boundaryManager: EpisodeBoundaryManager,
    private val modelRunner: ModelRunner,
    private val factExtractor: EpisodeFactExtractor,
    private val inferenceGate: BackgroundInferenceGate,
    private val quiet: SummaryQuietDelay,
    // [WHY] 드레인 항목(요약·사실 추출)을 시작하기 전에 입력이 멎기를 기다린다(0.41.7 M3) — 조용한 60초가 지나도 입력 중이면 GPU 를 다퉜다(M0).
    private val inputActivity: InputActivity = InputActivity(),
    // [WHY] 요약이 끝나면 그 기억의 뜻 벡터를 같은 흐름에서 만든다(0.41.7 M3) — null 이면(테스트) 하지 않는다.
    private val memoryEmbedder: MemoryEmbedder? = null
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // [WHY] 드레인과 catch-up 을 직렬화한다 — Ready 와 턴 종료가 겹치면 같은 에피소드를
    // 두 번 요약할 수 있다 (요약 = 추론 1회라 중복이 비싸다).
    // [WHY] 0.30.0: 수동 기억 정리와 같은 뮤텍스를 쓴다 — 정리 중 드레인은 정리가 끝난 뒤로 미뤄진다(BackgroundInferenceGate).
    private val drainMutex = inferenceGate.mutex
    private val deferred = ArrayDeque<String>()
    private val pendingExtractions = LinkedHashSet<String>()
    private val userActivitySeq = java.util.concurrent.atomic.AtomicLong(0)
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)
    private val quietLock = Any()
    private var waiting: Job? = null
    private var pendingCatchUp = false

    /**
     * 닫힘 방출 구독을 시작합니다 — KosmosApp.onCreate 가 부른다 (멱등).
     *
     * [WHY] init 에서 구독하던 것을 옮겼다(AGENTS §2-④) — Orchestrator 를 주입받는 Hilt 테스트가
     * 주입만으로 catch-up·요약 추론을 돌렸다. MorningBriefingGenerator.start() 와 같은 형태.
     * [WHY] 엔진 Ready 를 직접 구독하지 않는다(0.41.7 M2) — 시작 단계가 순서대로 부른다. 직접 구독하면 브리핑·미리 준비와 겹쳐 GPU 를 다툰다.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            boundaryManager.closedEpisodes.collect { closed ->
                synchronized(deferred) { deferred.addLast(closed.episodeId) }
            }
        }
    }

    /** 시작 단계가 끝났다 — 조용해지면 catch-up 과 드레인을 돌린다(예전의 Ready 편승 자리, 0.41.7 M2). */
    fun requestCatchUp() = whenQuiet(catchUp = true)

    /**
     * 30분 넘게 쉰 OPEN 을 닫고, 끝난 지 30분 넘은 미요약 에피소드 중 **가장 최근 것 하나**를 지금 요약합니다 — 요약했으면 true.
     *
     * [WHY] 쉬었다 돌아온 첫 말 전에 앞 에피소드 요약이 있어야 지난 대화를 요약 한 줄로 실을 수 있다(0.41.7 M1 exp81 — 원문이면 걸린 물음을
     * 이어받았다). 그래서 시작 단계(채팅을 열기 전, 진행 문구를 보이며)에서 하나만 한다 — 나머지와 사실 추출은 [requestCatchUp] 의 조용한 창으로.
     * @param onStart 요약 추론 직전에 부른다 — 할 일이 없으면 부르지 않아 진행 문구가 깜빡이지 않는다.
     */
    suspend fun summarizeLatestStale(now: Long = System.currentTimeMillis(), onStart: () -> Unit = {}): Boolean = drainMutex.withLock {
        closeStaleOpen(now)
        // [WHY] 30분 넘게 지난 것만 — 대화 예산 리셋으로 방금 닫힌 에피소드까지 여기서 하면 잠깐 나갔다 와도 요약을 기다린다.
        // 그런 것은 지금처럼 조용한 창에서 한다(지난 대화를 요약 한 줄로 바꾸는 것도 30분 기준이다).
        val latest = (episodeRepository.getByStatus(EpisodeStatus.CLOSED) as? AppResult.Success)
            ?.data.orEmpty()
            .filter { now - (it.endAt ?: it.startAt) > EpisodeBoundaryManager.IDLE_BOUNDARY_MS }
            .maxByOrNull { it.endAt ?: it.startAt }
            ?: return@withLock false
        onStart()
        synchronized(deferred) { deferred.remove(latest.id) }
        var summarized = false
        modelRunner.deferRestore {
            process(latest.id, now, userActivitySeq.get(), extract = false)
            summarized = (episodeRepository.getById(latest.id) as? AppResult.Success)?.data?.status == EpisodeStatus.SUMMARIZED
        }
        summarized
    }

    /** 오케스트레이터가 사용자 요청을 받자마자 호출 — 기다리던 요약을 미루고, 드레인이 항목 사이면 멈추게 한다. */
    fun onUserActivity() {
        userActivitySeq.incrementAndGet()
        synchronized(quietLock) {
            waiting?.cancel()
            waiting = null
        }
    }

    /** 오케스트레이터가 턴 종료 후 호출 — 조용해지면([SummaryQuietDelay]) 지연 큐를 비운다 (fire-and-forget). */
    fun onTurnCompleted() = whenQuiet(catchUp = false)

    /**
     * [quiet] 만큼 새 요청이 없으면 catch-up(Ready 때)과 드레인을 돌린다.
     *
     * [WHY] 요약은 엔진을 십수 초 붙잡아, 에피소드가 닫힌 직후에 보낸 질문이 그 뒤에서 기다렸다(0.39.0 M1 실측 — 첫 글자 17.1초).
     * 기다리는 동안 들어온 요청은 [onUserActivity] 가 대기를 취소한다. **이미 시작한 요약은 취소하지 않는다** — 추론 중 취소는
     * 대화를 망가뜨리는 경로다(0.38.6). 그래서 대기가 끝나는 순간 대기 표식을 지워, 이후의 취소가 실행 중인 요약에 닿지 않게 한다.
     */
    private fun whenQuiet(catchUp: Boolean) {
        synchronized(quietLock) {
            pendingCatchUp = pendingCatchUp || catchUp
            waiting?.cancel()
            waiting = scope.launch {
                delay(quiet.millis)
                val runCatchUp = synchronized(quietLock) {
                    if (waiting !== coroutineContext[Job]) return@launch
                    waiting = null
                    pendingCatchUp.also { pendingCatchUp = false }
                }
                if (runCatchUp) {
                    runCatchingCancellable { catchUp() }
                        .onFailure { AppLogger.e(TAG, "catch-up 실패", it) }
                }
                // [WHY] drain 도 감싼다 — 예외 한 번이 이 코루틴을 죽이면 이후 드레인이 조용히 멈춘다.
                drainSafely()
            }
        }
    }

    private suspend fun drainSafely() {
        runCatchingCancellable { drain() }
            .onFailure { AppLogger.e(TAG, "드레인 실패", it) }
    }

    private suspend fun drain(now: Long = System.currentTimeMillis()) = drainMutex.withLock {
        val activityAtStart = userActivitySeq.get()
        // [WHY] 스냅샷 배치 — 지금 큐에 있는 것만 처리한다. removeFirst 루프로 돌리면 발열
        // 게이트가 되돌려 넣은(addLast) 항목을 같은 루프가 즉시 다시 꺼내 **발열이 식을 때까지
        // 무한 회전**한다(테스트가 잡은 결함 — 뮤텍스를 쥔 채라 이후 드레인도 전부 막힌다).
        // 미뤄진 항목은 다음 드레인(다음 턴 종료/Ready)에서 재시도된다.
        val batch = synchronized(deferred) {
            val b = deferred.toList()
            deferred.clear()
            b
        }
        // [WHY] 에피소드 요약·사실 추출이 연속으로 돌 때 매 oneShot 마다 채팅 대화를 복원하지 않고 드레인이 끝날 때 한 번만 복원한다 (0.40.6 M3, exp73 defer).
        modelRunner.deferRestore {
            for (i in batch.indices) {
                inputActivity.awaitQuiet()
                // [WHY] 드레인이 요약 → 자동 추출 → 다음 에피소드를 연달아 돌아 그사이 들어온 질문이 기다렸다(0.39.1 M3·0.39.4 M0).
                // 돌고 있는 추론은 취소하지 않되(0.38.6 결함 경로), 항목 사이·추출 전에 새 요청이 있었으면 남은 항목을 큐 앞쪽으로 되돌리고 멈춘다.
                if (userActivitySeq.get() != activityAtStart) {
                    requeueFront(batch.subList(i, batch.size))
                    return@withLock
                }
                val interruptedDuringItem = process(batch[i], now, activityAtStart)
                if (interruptedDuringItem) {
                    requeueFront(batch.subList(i + 1, batch.size))
                    return@withLock
                }
            }
        }
    }

    private fun requeueFront(ids: List<String>) {
        synchronized(deferred) {
            for (id in ids.asReversed()) {
                if (id !in deferred) deferred.addFirst(id)
            }
        }
    }

    /**
     * @param extract false 면 요약만 하고 사실 추출은 다음 조용한 창으로 미룬다 — 시작 단계는 채팅을 막고 있으므로 하나만 한다([summarizeLatestStale]).
     */
    private suspend fun process(episodeId: String, now: Long, activityAtStart: Long, extract: Boolean = true): Boolean {
        // [WHY] 발열 게이트 — 경고 온도(43°C) 이상이면 미룬다. 요약은 급하지 않은 추론이고,
        // 다음 드레인(턴 종료/Ready)에서 재시도된다.
        if (metricsCollector.shouldDeferBackgroundInference()) {
            synchronized(deferred) { deferred.addLast(episodeId) }
            return false
        }

        val episode = (episodeRepository.getById(episodeId) as? AppResult.Success)?.data ?: return false
        val extractOnly = synchronized(deferred) { episodeId in pendingExtractions }
        if (extractOnly) {
            synchronized(deferred) { pendingExtractions.remove(episodeId) }
            if (episode.status != EpisodeStatus.SUMMARIZED) return false
            val messages = (conversationRepository.getByEpisode(episodeId) as? AppResult.Success)
                ?.data.orEmpty()
            if (messages.isEmpty()) return false
            runCatchingCancellable { factExtractor.extract(episode, messages) }
                .onFailure { AppLogger.w(TAG, "자동 추출 예외(${episode.id}): ${it.message}") }
            return false
        }
        if (episode.status != EpisodeStatus.CLOSED) return false // 이미 처리됐거나 종결

        val messages = (conversationRepository.getByEpisode(episodeId) as? AppResult.Success)
            ?.data.orEmpty()
        if (messages.isEmpty()) {
            // [WHY] 내용 없는 에피소드(경계 직후 강제종료 등)는 요약할 것이 없다 — 남겨 두면
            // catch-up 이 영원히 재시도하므로 지운다. 원문 메시지가 없으니 잃는 것도 없다.
            episodeRepository.delete(episodeId)
            return false
        }

        when (val result = summarizeEpisode(messages)) {
            is AppResult.Success -> {
                applyDocs(episode, result.data, now)
                runCatchingCancellable { memoryEmbedder?.prepareNewNow() }
                    .onFailure { AppLogger.w(TAG, "요약 뒤 기억 벡터 실패(${episode.id}): ${it.message}") }
                // [WHY] 요약 도중 사용자 요청이 들어왔으면 자동 추출은 다음 조용한 창으로 미룬다(0.39.4 M1).
                // 이미 SUMMARIZED 로 바뀌었으므로 pendingExtractions 에 표시해 두어 다음 창에서 재요약 없이 추출만 돈다.
                if (!extract || userActivitySeq.get() != activityAtStart) {
                    synchronized(deferred) {
                        pendingExtractions.add(episodeId)
                        if (episodeId !in deferred) deferred.addFirst(episodeId)
                    }
                    return true
                }
                inputActivity.awaitQuiet()
                // [WHY] 자동 추출(C′2)은 요약 **성공 직후 같은 드레인**에서 1회 — 발열 게이트를
                // 이미 지났고 llmDispatcher 경합도 없는 자리다. 실패·재시도 경로에서는 부르지
                // 않는다(재시도마다 중복 추출 방지). 추출 실패는 요약 상태에 영향을 주지 않는다.
                runCatchingCancellable { factExtractor.extract(episode.copy(tags = result.data.first().tags), messages) }
                    .onFailure { AppLogger.w(TAG, "자동 추출 예외(${episode.id}): ${it.message}") }
            }
            // [WHY] 조용해지기를 기다리는 사이 앱이 백그라운드로 가면 엔진이 내려간다 — 그건 요약 실패가 아니다. 재시도로 세지 않고
            // CLOSED 로 남겨 다음 Ready 의 catch-up 이 다시 큐에 넣게 한다.
            is AppResult.Failure if result.error is AppError.ModelNotReady ->
                AppLogger.d(TAG, "엔진이 내려가 요약을 다음 실행으로(${episode.id})")
            is AppResult.Failure -> {
                val retried = episode.copy(
                    retryCount = episode.retryCount + 1,
                    // [WHY] 상한 도달 시 FAILED 고정 — 아카이브에 노출되지 않지만 원문은
                    // 타임라인에 그대로다(요약 실패 ≠ 데이터 손실).
                    status = if (episode.retryCount + 1 >= MAX_RETRY) EpisodeStatus.FAILED
                    else EpisodeStatus.CLOSED,
                    updatedAt = now
                )
                episodeRepository.update(retried)
                AppLogger.w(TAG, "에피소드 요약 실패(${retried.retryCount}/${MAX_RETRY}): ${result.error}")
            }
        }
        return false
    }

    /**
     * 요약 문서를 반영합니다. 다중 주제면 첫 문서가 기존 행을, 나머지가 추가 행을 차지한다
     * (메시지 episodeId 는 그대로 — 원문 이동은 시간 범위가 같으므로 충분하다, M0 게이트).
     */
    private suspend fun applyDocs(episode: Episode, docs: List<SummarizeEpisodeUseCase.EpisodeDoc>, now: Long) {
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
     * 강제종료 복구 — 시작 단계가 끝난 뒤([requestCatchUp]) 조용해지면 1회씩.
     * ① 마지막 메시지가 30분 이상 과거인 OPEN → CLOSED
     * ② 미배정(episodeId NULL) 메시지 → 시간 간격으로 소급 에피소드 생성·배정
     * ③ 미요약 CLOSED → 큐 투입 (drain 이 이어서 처리)
     */
    internal suspend fun catchUp(now: Long = System.currentTimeMillis()) {

        // ① 낡은 OPEN 닫기
        closeStaleOpen(now)

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

    /** 마지막 메시지가 30분 넘게 지난 OPEN 을 닫는다(내용 없으면 지운다). */
    private suspend fun closeStaleOpen(now: Long) {
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
    }

    /**
     * 대화방별로 시간 간격(30분)으로 잘라 소급 에피소드를 만들고 메시지를 배정한다 (exp33 분절 규칙).
     *
     * [WHY] `getUnassigned()` 는 대화방 구분 없이 전체 시간순을 돌려준다 — 대화방별로 먼저 묶지 않으면
     * 서로 다른 대화방의 메시지가 한 에피소드로 섞인다(0.40.2 M1-3).
     */
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
        for ((_, sessionMessages) in messages.groupBy { it.sessionId }) {
            var prev: Long? = null
            for (m in sessionMessages) {
                if (prev != null && m.createdAt - prev > EpisodeBoundaryManager.IDLE_BOUNDARY_MS) flush()
                chunk.add(m)
                prev = m.createdAt
            }
            flush()
        }
    }

    private companion object {
        const val TAG = "EpisodeScheduler"
        const val MAX_RETRY = 3
    }
}
