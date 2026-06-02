package com.localfriday.app.ui.feature.chat

import com.localfriday.app.core.common.AppError
import com.localfriday.app.domain.assistant.approval.ApprovalRequest
import com.localfriday.app.domain.model.ChatMessage

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isInFlight: Boolean = false,
    val pendingApproval: ApprovalRequest? = null,
    val error: AppError? = null
)
