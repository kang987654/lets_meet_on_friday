package com.kosmos.app.assistant.approval

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ApprovalCoordinator]
 * 캘린더 등록·메모리 저장 등 중요 작업 실행 전 사용자 승인이 필요한 에이전트 액션을 중재하는 코디네이터 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Core State)
 * - **Dependencies**: None (State holder)
 *
 * ### Key Flow
 * 1. BaseAgent 공통 툴 실행 경로가 승인 대상 툴 실행 전 `requireApproval()`로 사용자 결정을 suspend 대기
 * 2. ViewModel이 `pendingRequest` 상태를 감지하여 다이얼로그 노출
 * 3. 사용자가 승인/거절 시 `approve()`/`reject()` 호출로 대기 해제 (제한 시간 초과 시 자동 거절)
 * 4. 승인 카드를 가리는 시스템 대화상자(캘린더 권한)가 닫히면 [restartTimeout] 으로 시간을 새로 준다
 */
@Singleton
class ApprovalCoordinator @Inject constructor() {
    private val _pendingRequest = MutableStateFlow<ApprovalRequest?>(null)
    val pendingRequest: StateFlow<ApprovalRequest?> = _pendingRequest.asStateFlow()

    // [WHY] 에이전트 코루틴(쓰기)과 ViewModel(읽기/완료)이 서로 다른 스레드에서 접근하므로
    // plain var 대신 AtomicReference로 결정 대기 상태를 관리한다.
    private val currentDecision = AtomicReference<CompletableDeferred<Boolean>?>(null)

    // [WHY] CONFLATED — 재시작 신호는 "한 번 더 60초"라는 사실만 전하면 된다(여러 번 쌓일 이유가 없다).
    private val timeoutRestart = Channel<Unit>(Channel.CONFLATED)

    private enum class Wait { APPROVED, REJECTED, RESTART }

    suspend fun requireApproval(request: ApprovalRequest): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        // 이전 미결 요청이 남아 있으면 거절 처리하고 새 요청으로 교체
        currentDecision.getAndSet(deferred)?.complete(false)
        _pendingRequest.value = request
        // 이전 요청 시절에 남은 재시작 신호가 새 요청의 시간을 늘리지 않게 비운다.
        timeoutRestart.tryReceive()
        return try {
            // [WHY] 승인 UI가 유실돼도 툴 루프가 영원히 매달리지 않도록 제한 시간 초과 시 자동 거절한다.
            // [WHY] 재시작 루프 — 승인 카드와 캘린더 권한 대화상자가 동시에 뜨면 대화상자가 카드를
            // 가리는 동안에도 60초가 흘렀다(2026-09-30 에뮬레이터: 무응답 → 자동 거절). 대화상자 결과가
            // 오면 시간을 새로 준다. 아무도 답하지 않으면 여전히 제한 시간 뒤 거절된다.
            var outcome: Wait
            do {
                outcome = withTimeoutOrNull(APPROVAL_TIMEOUT_MS) {
                    select {
                        deferred.onAwait { approved -> if (approved) Wait.APPROVED else Wait.REJECTED }
                        timeoutRestart.onReceive { Wait.RESTART }
                    }
                } ?: Wait.REJECTED
            } while (outcome == Wait.RESTART)
            outcome == Wait.APPROVED
        } finally {
            _pendingRequest.value = null
            currentDecision.compareAndSet(deferred, null)
        }
    }

    fun approve() {
        currentDecision.getAndSet(null)?.complete(true)
        _pendingRequest.value = null
    }

    fun reject() {
        currentDecision.getAndSet(null)?.complete(false)
        _pendingRequest.value = null
    }

    /**
     * 대기 중인 승인 요청의 제한 시간을 처음부터 다시 셉니다. 대기 중인 요청이 없으면 효과가 없다.
     */
    fun restartTimeout() {
        timeoutRestart.trySend(Unit)
    }

    /**
     * 대기 중인 요청의 UI 상태만 소비합니다 — 결정 대기(deferred)는 유지되므로
     * 호출 측은 반드시 [approve] 또는 [reject]로 결정을 완료해야 합니다.
     */
    fun consumePending(): ApprovalRequest? {
        val current = _pendingRequest.value
        _pendingRequest.value = null
        return current
    }

    internal companion object {
        const val APPROVAL_TIMEOUT_MS = 60_000L
    }
}
