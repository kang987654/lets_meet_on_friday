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
    /** 위젯 🎤·QS 타일 진입 — 화면이 권한 게이트를 지나 녹음을 자동 시작한 뒤 소비한다 (A3). */
    val pendingVoiceStart: Boolean = false,
    /** 웹 검색이 허용됐으나 실패한 턴이면 true — 화면이 한 번 안내한 뒤 소비한다. */
    val searchFailedNotice: Boolean = false,
    val deviceStatus: com.kosmos.app.runtime.metrics.DeviceStatus =
        com.kosmos.app.runtime.metrics.DeviceStatus(),
    /** 자동 추출(C′2)의 프로필 제안 대기 목록 — 첫 항목이 입력바 위 카드로 뜬다. */
    val pendingSuggestions: ImmutableList<com.kosmos.app.domain.model.ProfileSuggestion> = persistentListOf(),
    /** 제안 승인/거절 결과 한 줄 안내(스낵바) — 화면이 한 번 보인 뒤 소비한다. */
    val suggestionNotice: String? = null
)
