package com.kosmos.app.feature.approval

import com.kosmos.app.domain.model.CalendarDraft

sealed class ApprovalUiState {
    object Idle : ApprovalUiState()
    object Loading : ApprovalUiState()
    object Success : ApprovalUiState()
    object Empty : ApprovalUiState()
    data class Error(val message: String) : ApprovalUiState()
}
