package com.localfriday.app.ui.feature.chat

import com.localfriday.app.core.common.AppError
import com.localfriday.app.assistant.approval.ApprovalRequest
import com.localfriday.app.domain.model.ChatMessage
import com.localfriday.app.domain.modelrunner.ModelLoadState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class ChatUiState(
    val sessionId: String = "",
    val messages: ImmutableList<ChatMessage> = persistentListOf(),
    val isInFlight: Boolean = false,
    val pendingApproval: ApprovalRequest? = null,
    val sharedInput: com.localfriday.app.platform.share.SharedInput? = null,
    val error: AppError? = null,
    val streamingText: String? = null,
    val warningMessage: String? = null,
    val engineState: ModelLoadState = ModelLoadState.Loading
)
