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
    val streamingThinking: String? = null,
    val warningMessage: String? = null,
    val engineState: ModelLoadState = ModelLoadState.Loading,
    val isRecording: Boolean = false,
    /** 웹 검색이 허용됐으나 실패한 턴이면 true — 화면이 한 번 안내한 뒤 소비한다. */
    val searchFailedNotice: Boolean = false,
    val deviceStatus: com.kosmos.app.runtime.metrics.DeviceStatus =
        com.kosmos.app.runtime.metrics.DeviceStatus()
)
