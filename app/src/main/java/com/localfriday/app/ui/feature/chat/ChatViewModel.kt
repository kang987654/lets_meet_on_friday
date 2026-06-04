package com.localfriday.app.ui.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.agent.AgentResult
import com.localfriday.app.domain.assistant.approval.ApprovalCoordinator
import com.localfriday.app.domain.memory.ConversationRepository
import com.localfriday.app.data.local.prefs.SessionStore
import com.localfriday.app.domain.model.ChatMessage
import com.localfriday.app.domain.model.InputType
import com.localfriday.app.domain.usecase.SendChatMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

import com.localfriday.app.domain.usecase.ResumeActionUseCase
import com.localfriday.app.platform.share.ShareIntentHandler

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionStore: SessionStore,
    private val conversationRepository: ConversationRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val resumeActionUseCase: ResumeActionUseCase,
    private val approvalCoordinator: ApprovalCoordinator,
    private val shareIntentHandler: ShareIntentHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val sessionId: String

    init {
        val savedSessionId = savedStateHandle.get<String>(KEY_SESSION_ID)
        sessionId = savedSessionId ?: UUID.randomUUID().toString()
        
        savedStateHandle[KEY_SESSION_ID] = sessionId
        _uiState.update { it.copy(sessionId = sessionId) }
        viewModelScope.launch {
            sessionStore.saveActiveSessionId(sessionId)
        }

        observePendingApproval()
        observeSharedInput()
        loadMessages()
    }

    private fun observeSharedInput() {
        viewModelScope.launch {
            shareIntentHandler.sharedInputFlow.collectLatest { result ->
                when (result) {
                    is AppResult.Success -> {
                        _uiState.update { it.copy(sharedInput = result.data, error = null) }
                    }
                    is AppResult.Failure -> {
                        _uiState.update { it.copy(error = result.error) }
                    }
                }
            }
        }
    }

    private fun observePendingApproval() {
        viewModelScope.launch {
            approvalCoordinator.pendingRequest.collectLatest { pending ->
                _uiState.update { it.copy(pendingApproval = pending) }
            }
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            val result = conversationRepository.getRecentBySession(sessionId)
            if (result is AppResult.Success) {
                _uiState.update { state -> 
                    state.copy(messages = result.data) 
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isInFlight) return

        // 낙관적 업데이트 (Optimistic Append)
        val tempUserMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = ChatMessage.Role.USER,
            content = text,
            inputType = InputType.TEXT,
            createdAt = System.currentTimeMillis()
        )
        
        _uiState.update { state -> 
            state.copy(
                messages = state.messages + tempUserMessage,
                isInFlight = true,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                val result = sendChatMessageUseCase(sessionId = sessionId, message = text)
                handleAgentResult(result)
            } finally {
                _uiState.update { it.copy(isInFlight = false) }
            }
        }
    }

    fun clearSharedInput() {
        _uiState.update { it.copy(sharedInput = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun approvePendingRequest() {
        val request = approvalCoordinator.consumePending() ?: return
        
        _uiState.update { it.copy(isInFlight = true, error = null) }
        viewModelScope.launch {
            try {
                val result = resumeActionUseCase(sessionId, request.action)
                handleAgentResult(result)
            } finally {
                _uiState.update { it.copy(isInFlight = false) }
            }
        }
    }

    private fun handleAgentResult(result: AppResult<AgentResult>) {
        when (result) {
            is AppResult.Success -> {
                when (val agentResult = result.data) {
                    is AgentResult.Text -> {
                        val assistantMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            role = ChatMessage.Role.ASSISTANT,
                            content = agentResult.content,
                            inputType = InputType.TEXT,
                            createdAt = System.currentTimeMillis()
                        )
                        _uiState.update { it.copy(messages = it.messages + assistantMessage) }
                    }
                    is AgentResult.ActionRequired -> {
                        // approvalCoordinator가 플로우에 값을 방출하므로 자동 처리됨
                    }
                    is AgentResult.Error -> {
                        _uiState.update { it.copy(error = agentResult.error) }
                    }
                }
            }
            is AppResult.Failure -> {
                _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    fun rejectPendingRequest() {
        val request = approvalCoordinator.consumePending() ?: return
        // 거절 시
        _uiState.update { state ->
            val sysMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = ChatMessage.Role.ASSISTANT,
                content = "보안 정책에 의해 작업이 거부되었습니다.",
                inputType = InputType.TEXT,
                createdAt = System.currentTimeMillis()
            )
            state.copy(messages = state.messages + sysMessage)
        }
    }

    companion object {
        private const val KEY_SESSION_ID = "chat_session_id"
    }
}
