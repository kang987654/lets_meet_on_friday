package com.kosmos.app.feature.chat

import com.kosmos.app.core.common.AppError
import com.kosmos.app.assistant.approval.ApprovalRequest
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.modelrunner.ModelLoadState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class ChatUiState(
    val sessionId: String = "",
    val messages: ImmutableList<ChatMessage> = persistentListOf(),
    val isInFlight: Boolean = false,
    val pendingApproval: ApprovalRequest? = null,
    val sharedInput: com.kosmos.app.platform.share.SharedInput? = null,
    val error: AppError? = null,
    val streamingText: String? = null,
    val warningMessage: String? = null,
    val engineState: ModelLoadState = ModelLoadState.Loading,
    val isRecording: Boolean = false
)
