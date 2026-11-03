package com.kosmos.app.assistant.orchestrator

import com.kosmos.app.assistant.agent.KosmosAgent
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * [ConversationPrewarmer]
 * 턴이 끝나거나 엔진이 준비(Ready)되면 다음 턴이 쓸 대화를 뒤에서 미리 준비합니다 — 다시 만들어야 할 때만 실제로 일한다(판정은 모델 런너).
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Orchestration) — [AssistantOrchestrator] 가 요청 시작([onUserActivity])과 턴 끝([afterTurn])에 부르고, [com.kosmos.app.KosmosApp] 이 [start] 를 부른다.
 * - **Dependencies**: [ContextBuilder](다음 턴과 같은 기록), [KosmosAgent](같은 지시·툴로 프롬프트 조립), [ModelRunner], [SessionStore]
 *
 * ### Key Flow
 * 1. 엔진 Ready 전이 → 현재 활성 대화방으로 대화를 미리 준비한다(첫 실행·복귀 뒤 첫 답 대기 단축, 0.39.4 M2).
 * 2. 턴 끝 → 이전 준비를 취소하고 새 준비를 띄운다(fire-and-forget — 답 반환을 막지 않는다).
 *    준비는 턴 끝·마지막 입력([onTyping]) 중 늦은 시각부터 [Constants.PREWARM_QUIET_MS] 조용해진 뒤 시작한다.
 * 3. 새 요청 시작 → 아직 엔진에 닿지 않은 준비를 취소한다. 엔진 안에서 도는 준비는 끝까지 간다(네이티브 추론 중 취소는 0.38.6 결함 경로).
 *
 * [WHY] 재생성 턴의 +6~7초와 첫 실행·복귀 뒤 첫 답의 문법 준비+프리필(약 9~10초)을 사용자가 질문하기 전에 미리 치른다(0.39.2 M3·0.39.4 M2, exp66).
 * [WHY] 조용해질 때까지 기다린다 — 재생성 프리필은 GPU 를 수 초 붙잡아, 답 직후 바로 치는 입력의 화면 프레임이 GPU 대기로 0.4초씩 밀렸다(0.40.7).
 */
@Singleton
class ConversationPrewarmer @Inject constructor(
    private val contextBuilder: ContextBuilder,
    private val modelRunner: ModelRunner,
    private val sessionStore: SessionStore,
    private val agent: Provider<KosmosAgent>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)
    private val lock = Any()
    private var job: Job? = null
    @Volatile private var lastActivityAt = 0L

    // [WHY] 테스트가 기다림을 줄이는 손잡이 — Hilt 생성자에 Long 을 넣으면 바인딩이 필요해진다.
    internal var quietMs: Long = Constants.PREWARM_QUIET_MS

    /**
     * 엔진 Ready 편승 구독을 시작합니다 — KosmosApp.onCreate 가 부른다 (멱등).
     *
     * [WHY] init 에서 구독하지 않는다(AGENTS §2-④) — Hilt 테스트가 주입만으로 백그라운드 준비를 돌리지 않게 한다.
     * 전경 진입(ModelWarmUpObserver)이 warmUp 을 불러 Ready 가 되므로 §2-⑥(백그라운드 로드 금지) 안이다.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            modelRunner.loadState.collect { state ->
                if (state is ModelLoadState.Ready) {
                    val sessionId = sessionStore.activeSessionIdFlow.first()
                        ?: UUID.randomUUID().toString().also { sessionStore.saveActiveSessionId(it) }
                    afterTurn(sessionId)
                }
            }
        }
    }

    /** 턴이 끝났다 — 입력이 멎으면 다음 턴의 대화를 미리 준비한다. */
    fun afterTurn(sessionId: String) {
        synchronized(lock) {
            job?.cancel()
            val turnEndedAt = System.currentTimeMillis()
            job = scope.launch {
                awaitQuiet(turnEndedAt)
                val context = (contextBuilder.build(sessionId) as? AppResult.Success)?.data ?: return@launch
                // [WHY] 기록을 읽는 사이에 새 요청이 왔으면 여기서 멈춘다 — 엔진 쪽 확인(잠금 안)보다 앞서 거른다.
                ensureActive()
                agent.get().prepareNextTurn(context)
            }
        }
    }

    /** 새 요청이 시작됐다 — 엔진에 닿기 전의 준비를 멈춘다. */
    fun onUserActivity() {
        synchronized(lock) {
            job?.cancel()
            job = null
        }
    }

    /**
     * 입력란 글자 변경·녹음 시작 — 기다리는 준비가 조용한 시간을 처음부터 다시 센다.
     *
     * @param now 조용한 시간을 세기 시작할 시각. 녹음은 상한이 끝나는 시각을 넘겨 녹음 내내 미룬다.
     */
    fun onTyping(now: Long = System.currentTimeMillis()) {
        lastActivityAt = now
    }

    private suspend fun awaitQuiet(turnEndedAt: Long) {
        while (true) {
            val remaining = quietRemainingMs(maxOf(turnEndedAt, lastActivityAt), System.currentTimeMillis(), quietMs)
            if (remaining <= 0) return
            delay(remaining)
        }
    }
}

/** 마지막 활동 뒤 [quietMs] 조용해지기까지 남은 시간(ms) — 이미 지났으면 0. */
internal fun quietRemainingMs(lastActivityAt: Long, now: Long, quietMs: Long): Long =
    (lastActivityAt + quietMs - now).coerceAtLeast(0)
