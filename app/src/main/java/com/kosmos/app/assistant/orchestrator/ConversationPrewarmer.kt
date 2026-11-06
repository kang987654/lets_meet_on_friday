package com.kosmos.app.assistant.orchestrator

import com.kosmos.app.assistant.agent.KosmosAgent
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.logging.AppLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * [ConversationPrewarmer]
 * 턴이 끝나거나 엔진이 준비(Ready)되면 다음 턴이 쓸 대화를 미리 준비합니다 — 다시 만들어야 할 때만 실제로 일한다(판정은 모델 런너).
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Orchestration) — [AssistantOrchestrator] 가 요청 시작([onUserActivity])과 턴 끝([afterTurn])에,
 *   시작 단계([com.kosmos.app.assistant.startup.StartupCoordinator])가 엔진 Ready 뒤 [prepareNow] 를 부른다.
 * - **Dependencies**: [ContextBuilder](다음 턴과 같은 기록), [KosmosAgent](같은 지시·툴로 프롬프트 조립), [ModelRunner]
 *
 * ### Key Flow
 * 1. 엔진 Ready 뒤 → 시작 단계가 [prepareNow] 로 기다림 없이 준비하고 끝나야 채팅을 연다(첫 실행·복귀 뒤 첫 답 대기 단축 0.39.4 M2,
 *    채팅을 연 뒤 준비가 돌면 입력이 0.85초씩 멈췄다 — 0.41.7 M0).
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
    private val agent: Provider<KosmosAgent>
) {
    // [WHY] 미리 준비는 최선 노력이다 — 처리기가 없으면 준비 중 예외 하나가 기본 스레드 처리기로 가 앱을 죽인다.
    // 테스트에서는 앞 테스트의 준비가 다음 테스트 도중 끝난 Hilt 컴포넌트를 건드려 그 테스트를 실패시켰다(0.41.5, Robolectric 4.17).
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> AppLogger.e(TAG, "대화 미리 준비 실패", e) }
    )
    private val lock = Any()
    private var job: Job? = null
    @Volatile private var lastActivityAt = 0L

    // [WHY] 테스트가 기다림을 줄이는 손잡이 — Hilt 생성자에 Long 을 넣으면 바인딩이 필요해진다.
    internal var quietMs: Long = Constants.PREWARM_QUIET_MS

    /** 턴이 끝났다 — 입력이 멎으면 다음 턴의 대화를 미리 준비한다. */
    fun afterTurn(sessionId: String) {
        launchPrepare(sessionId, quietFrom = System.currentTimeMillis())
    }

    /**
     * 지금 준비하고 끝날 때까지 기다린다 — 시작 단계가 채팅을 열기 전에 부른다(조용함을 기다리지 않는다, 0.41.7 M2).
     * 새 요청([onUserActivity])이 오면 엔진에 닿기 전의 준비는 멈추고 바로 돌아온다.
     */
    suspend fun prepareNow(sessionId: String) {
        launchPrepare(sessionId, quietFrom = null).join()
    }

    private fun launchPrepare(sessionId: String, quietFrom: Long?): Job = synchronized(lock) {
        job?.cancel()
        scope.launch {
            if (quietFrom != null) awaitQuiet(quietFrom)
            val context = (contextBuilder.build(sessionId) as? AppResult.Success)?.data ?: return@launch
            // [WHY] 기록을 읽는 사이에 새 요청이 왔으면 여기서 멈춘다 — 엔진 쪽 확인(잠금 안)보다 앞서 거른다.
            ensureActive()
            agent.get().prepareNextTurn(context)
        }.also { job = it }
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

    private companion object {
        const val TAG = "ConversationPrewarmer"
    }
}

/** 마지막 활동 뒤 [quietMs] 조용해지기까지 남은 시간(ms) — 이미 지났으면 0. */
internal fun quietRemainingMs(lastActivityAt: Long, now: Long, quietMs: Long): Long =
    (lastActivityAt + quietMs - now).coerceAtLeast(0)
