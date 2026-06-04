package com.localfriday.app.ui.feature.chat

import com.localfriday.app.core.common.AppError
import com.localfriday.app.domain.assistant.approval.ApprovalRequest
import com.localfriday.app.domain.model.ChatMessage

data class ChatUiState(
    val sessionId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val isInFlight: Boolean = false,
    val pendingApproval: ApprovalRequest? = null,
    val sharedInput: com.localfriday.app.platform.share.SharedInput? = null,
    val error: AppError? = null,
    val streamingText: String? = null
)
