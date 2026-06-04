package com.localfriday.app.feature.approval

import com.localfriday.app.domain.model.CalendarDraft

sealed class ApprovalUiState {
    object Idle : ApprovalUiState()
    object Saving : ApprovalUiState()
    object Success : ApprovalUiState()
    data class Error(val message: String) : ApprovalUiState()
}
