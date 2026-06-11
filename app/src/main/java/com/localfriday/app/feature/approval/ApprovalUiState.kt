package com.localfriday.app.feature.approval

import com.localfriday.app.domain.model.CalendarDraft

sealed class ApprovalUiState {
    object Idle : ApprovalUiState()
    object Loading : ApprovalUiState()
    object Success : ApprovalUiState()
    object Empty : ApprovalUiState()
    data class Error(val message: String) : ApprovalUiState()
}
