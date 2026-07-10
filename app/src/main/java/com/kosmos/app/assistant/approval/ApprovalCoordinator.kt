package com.kosmos.app.assistant.approval

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ApprovalCoordinator]
 * 웹 검색이나 중요 작업 실행 전 사용자 승인이 필요한 에이전트 액션을 중재하는 코디네이터 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Core State)
 * - **Dependencies**: None (State holder)
 *
 * ### Key Flow
 * 1. Agent가 위험한 행동(웹 검색 등) 실행 전 `requestApproval()` 호출
 * 2. ViewModel이 `pendingRequest` 상태를 감지하여 다이얼로그 노출
 * 3. 사용자가 승인/거절 시 `consumePending()` 또는 `clearPending()` 수행
 */
@Singleton
class ApprovalCoordinator @Inject constructor() {
    private val _pendingRequest = MutableStateFlow<ApprovalRequest?>(null)
    val pendingRequest: StateFlow<ApprovalRequest?> = _pendingRequest.asStateFlow()

    fun requestApproval(request: ApprovalRequest) {
        _pendingRequest.value = request
    }

    fun consumePending(): ApprovalRequest? {
        val current = _pendingRequest.value
        _pendingRequest.value = null
        return current
    }

    fun clearPending() {
        _pendingRequest.value = null
    }
}
