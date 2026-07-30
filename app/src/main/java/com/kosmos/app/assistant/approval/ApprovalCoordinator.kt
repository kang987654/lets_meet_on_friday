package com.kosmos.app.assistant.approval

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 */
@Singleton
class ApprovalCoordinator @Inject constructor() {
    private val _pendingRequest = MutableStateFlow<ApprovalRequest?>(null)
    val pendingRequest: StateFlow<ApprovalRequest?> = _pendingRequest.asStateFlow()

    // [WHY] 에이전트 코루틴(쓰기)과 ViewModel(읽기/완료)이 서로 다른 스레드에서 접근하므로
    // plain var 대신 AtomicReference로 결정 대기 상태를 관리한다.
    private val currentDecision = AtomicReference<CompletableDeferred<Boolean>?>(null)

    suspend fun requireApproval(request: ApprovalRequest): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        // 이전 미결 요청이 남아 있으면 거절 처리하고 새 요청으로 교체
        currentDecision.getAndSet(deferred)?.complete(false)
        _pendingRequest.value = request
        return try {
            // [WHY] 승인 UI가 유실돼도 툴 루프가 영원히 매달리지 않도록 제한 시간 초과 시 자동 거절한다.
            withTimeoutOrNull(APPROVAL_TIMEOUT_MS) { deferred.await() } ?: false
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
     * 대기 중인 요청의 UI 상태만 소비합니다 — 결정 대기(deferred)는 유지되므로
     * 호출 측은 반드시 [approve] 또는 [reject]로 결정을 완료해야 합니다.
     */
    fun consumePending(): ApprovalRequest? {
        val current = _pendingRequest.value
        _pendingRequest.value = null
        return current
    }

    fun clearPending() {
        currentDecision.getAndSet(null)?.complete(false)
        _pendingRequest.value = null
    }

    private companion object {
        const val APPROVAL_TIMEOUT_MS = 60_000L
    }
}
