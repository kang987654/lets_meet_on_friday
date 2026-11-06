package com.kosmos.app.assistant.startup

import com.kosmos.app.assistant.briefing.MorningBriefingGenerator
import com.kosmos.app.assistant.episode.EpisodeSummarizeScheduler
import com.kosmos.app.assistant.orchestrator.ConversationPrewarmer
import com.kosmos.app.core.common.runCatchingCancellable
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.startup.StartupProgress
import com.kosmos.app.domain.startup.StartupStage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [StartupCoordinator]
 * 엔진이 준비될 때마다 채팅을 열기 전에 할 일을 차례로 돌립니다 (0.41.7 M2) — 지난 대화 정리 → 오늘 브리핑 → 대화 미리 준비.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Startup) — [com.kosmos.app.KosmosApp] 이 [start], 오케스트레이터가 요청마다 [onUserActivity].
 * - **Dependencies**: [ModelRunner](Ready 전이), [EpisodeSummarizeScheduler], [MorningBriefingGenerator], [ConversationPrewarmer], [SessionStore]
 * - 화면(스플래시·채팅 머리글)은 [StartupProgress.stage] 로 지금 단계를 보인다 — 스플래시는 [StartupStage.DONE] 이 되어야 채팅으로 간다.
 *
 * ### Key Flow
 * 1. 엔진을 올리는 중이면 [StartupStage.WAITING_ENGINE], 오류·모델 없음이면 [StartupStage.IDLE](화면은 엔진 상태로 안내).
 * 2. Ready → ① 30분 넘게 쉰 가장 최근 에피소드 하나 요약(있을 때만) ② 오늘 브리핑(아직 없을 때만) ③ 대화 미리 준비 → [StartupStage.DONE].
 * 3. 끝나면 남은 catch-up(밀린 요약·사실 추출)을 조용한 창에 맡긴다([EpisodeSummarizeScheduler.requestCatchUp]).
 *    부수 추론 뒤 복원 신호([ModelRunner.conversationRestoreNeeded])가 오면 대화 미리 준비를 건다(입력이 멎은 뒤, 0.41.7 M3).
 * 4. 그 사이 사용자가 요청을 보내면([onUserActivity]) 남은 단계를 건너뛴다 — 엔진 안에서 도는 일은 끝까지 간다(0.38.6 결함 경로).
 *
 * [WHY] 셋을 각자 Ready 에 편승시키면 채팅이 열린 뒤 GPU 를 수 초씩 붙잡아 입력이 0.8~0.9초씩 멈췄다(0.41.7 M0 실기기 — 요약 약 7초, 미리 준비 약 7초).
 * 채팅을 열기 전에 이유를 보이며 하고(사용자 결정 2026-11-06), 화면이 뜬 뒤에는 GPU 를 쓰지 않게 한다.
 */
@Singleton
class StartupCoordinator @Inject constructor(
    private val modelRunner: ModelRunner,
    private val summarizeScheduler: EpisodeSummarizeScheduler,
    private val briefingGenerator: MorningBriefingGenerator,
    private val prewarmer: ConversationPrewarmer,
    private val sessionStore: SessionStore
) : StartupProgress {

    private val _stage = MutableStateFlow(StartupStage.IDLE)
    override val stage: StateFlow<StartupStage> = _stage.asStateFlow()

    // [WHY] 시작 단계는 최선 노력이다 — 한 단계의 예외가 기본 스레드 처리기로 가 앱을 죽이지 않게 한다(ConversationPrewarmer 와 같은 결).
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> AppLogger.e(TAG, "시작 단계 실패", e) }
    )
    private val started = AtomicBoolean(false)
    private val userActivitySeq = AtomicLong(0)
    private val lock = Any()
    private var run: Job? = null

    // [WHY] 엔진 상태가 Ready→로딩→Ready 로 흔들려도(재진입 요동, 0.16.2) 앞 실행을 취소하지 않는다 — 엔진 안에서 도는 요약·브리핑을
    // 끊으면 대화를 망가뜨리는 경로다(0.38.6). 대신 세대를 올려, 낡은 실행은 단계 표시를 바꾸지 못하고 새 실행은 앞 실행이 끝난 뒤 돈다.
    @Volatile private var generation = 0L

    /**
     * 엔진 상태 구독을 시작합니다 — KosmosApp.onCreate 가 부른다 (멱등).
     *
     * [WHY] init 에서 구독하지 않는다(AGENTS §2-④) — Hilt 테스트가 주입만으로 시작 단계를 돌리지 않게 한다.
     * 전경 진입(ModelWarmUpObserver)이 warmUp 을 불러 Ready 가 되므로 §2-⑥(백그라운드 로드 금지) 안이다.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { modelRunner.loadState.collect { onLoadState(it) } }
        // [WHY] 부수 추론(요약·추출·정리) 뒤 채팅 대화를 되살리는 일을 대화 미리 준비 하나로 맡긴다(0.41.7 M3) — 입력이 멎은 뒤에만 돌아
        // 입력과 GPU 를 다투지 않는다. 예전에는 oneShot 직후 엔진이 바로 되살렸다(실기기 3.3~3.5초).
        scope.launch {
            modelRunner.conversationRestoreNeeded.collect {
                val sessionId = sessionStore.activeSessionIdFlow.first() ?: return@collect
                prewarmer.afterTurn(sessionId)
            }
        }
    }

    internal fun onLoadState(state: ModelLoadState) {
        synchronized(lock) {
            when (state) {
                is ModelLoadState.Ready -> {
                    val gen = ++generation
                    val previous = run
                    _stage.value = StartupStage.WAITING_ENGINE
                    run = scope.launch {
                        previous?.join()
                        runStages(gen)
                    }
                }
                is ModelLoadState.Error, is ModelLoadState.NotFound -> {
                    generation++
                    _stage.value = StartupStage.IDLE
                }
                else -> {
                    generation++
                    _stage.value = StartupStage.WAITING_ENGINE
                }
            }
        }
    }

    override fun onUserActivity() {
        userActivitySeq.incrementAndGet()
        synchronized(lock) {
            if (_stage.value.running) _stage.value = StartupStage.DONE
        }
    }

    internal suspend fun runStages(gen: Long = generation, now: Long = System.currentTimeMillis()) {
        val activityAtStart = userActivitySeq.get()
        val stale = { gen != generation }
        val interrupted = { stale() || userActivitySeq.get() != activityAtStart }
        // [WHY] 사용자가 요청을 보낸 뒤에는 단계 표시를 되살리지 않는다 — 머리글이 "정리 중"으로 돌아가면 답을 기다리는 화면과 어긋난다.
        val show = { next: StartupStage -> synchronized(lock) { if (!interrupted()) _stage.value = next } }

        if (stale()) return
        if (!interrupted()) {
            runCatchingCancellable { summarizeScheduler.summarizeLatestStale(now) { show(StartupStage.SUMMARIZING) } }
                .onFailure { AppLogger.e(TAG, "지난 대화 정리 실패", it) }
        }
        if (!interrupted()) {
            runCatchingCancellable { briefingGenerator.maybeGenerate(now) { show(StartupStage.BRIEFING) } }
                .onFailure { AppLogger.e(TAG, "브리핑 생성 실패", it) }
        }
        if (!interrupted()) {
            show(StartupStage.PREPARING)
            runCatchingCancellable {
                val sessionId = sessionStore.activeSessionIdFlow.first()
                    ?: UUID.randomUUID().toString().also { sessionStore.saveActiveSessionId(it) }
                prewarmer.prepareNow(sessionId)
            }.onFailure { AppLogger.e(TAG, "대화 미리 준비 실패", it) }
        }
        if (stale()) return
        synchronized(lock) { if (!stale()) _stage.value = StartupStage.DONE }
        summarizeScheduler.requestCatchUp()
    }

    private companion object {
        const val TAG = "StartupCoordinator"
    }
}
