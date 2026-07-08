package com.localfriday.app.assistant.approval

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

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
