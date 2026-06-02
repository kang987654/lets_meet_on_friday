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

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionStore: SessionStore,
    private val conversationRepository: ConversationRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val approvalCoordinator: ApprovalCoordinator
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
        loadMessages()
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
                                // approvalCoordinator가 pendingRequest 플로우에 값을 방출하므로
                                // observePendingApproval()이 알아서 UI에 띄워 줌
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
            } finally {
                // 어떤 경우든 통신이 끝나면 로딩 인디케이터 해제
                _uiState.update { it.copy(isInFlight = false) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        private const val KEY_SESSION_ID = "chat_session_id"
    }
}
