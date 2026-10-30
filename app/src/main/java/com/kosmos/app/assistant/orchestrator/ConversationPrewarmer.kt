package com.kosmos.app.assistant.orchestrator

import com.kosmos.app.assistant.agent.KosmosAgent
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.core.common.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * [ConversationPrewarmer]
 * 턴이 끝나면 다음 턴이 쓸 대화를 뒤에서 미리 준비합니다 — 다시 만들어야 할 때만 실제로 일한다(판정은 모델 런너).
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Orchestration) — [AssistantOrchestrator] 가 요청 시작([onUserActivity])과 턴 끝([afterTurn])에 부른다.
 * - **Dependencies**: [ContextBuilder](다음 턴과 같은 기록), [KosmosAgent](같은 지시·툴로 프롬프트 조립)
 *
 * ### Key Flow
 * 1. 턴 끝 → 이전 준비를 취소하고 새 준비를 띄운다(fire-and-forget — 답 반환을 막지 않는다).
 * 2. 새 요청 시작 → 아직 엔진에 닿지 않은 준비를 취소한다. 엔진 안에서 도는 준비는 끝까지 간다(네이티브 추론 중 취소는 0.38.6 결함 경로).
 *
 * [WHY] 재생성 턴의 +6~7초(0.39.0 M1)를 사용자가 답을 읽는 동안 치른다(0.39.2 M3, exp66).
 */
@Singleton
class ConversationPrewarmer @Inject constructor(
    private val contextBuilder: ContextBuilder,
    private val agent: Provider<KosmosAgent>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var job: Job? = null

    /** 턴이 끝났다 — 다음 턴의 대화를 미리 준비한다. */
    fun afterTurn(sessionId: String) {
        synchronized(lock) {
            job?.cancel()
            job = scope.launch {
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
}
