package com.localfriday.app.ui.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.agent.AgentResult
import com.localfriday.app.assistant.approval.ApprovalCoordinator
import com.localfriday.app.domain.memory.ConversationRepository
import com.localfriday.app.data.local.prefs.SessionStore
import com.localfriday.app.domain.model.ChatMessage
import com.localfriday.app.domain.model.InputType
import com.localfriday.app.domain.usecase.SendChatMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList
import java.util.UUID
import javax.inject.Inject

import com.localfriday.app.domain.usecase.ResumeActionUseCase
import com.localfriday.app.platform.share.ShareIntentHandler
import com.localfriday.app.runtime.metrics.RuntimeMetricsCollector
import com.localfriday.app.domain.modelrunner.ModelRunner

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val sessionStore: SessionStore,
    private val conversationRepository: ConversationRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val resumeActionUseCase: ResumeActionUseCase,
    private val approvalCoordinator: ApprovalCoordinator,
    private val shareIntentHandler: ShareIntentHandler,
    private val runtimeMetricsCollector: RuntimeMetricsCollector,
    private val modelRunner: ModelRunner
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var sessionId: String = UUID.randomUUID().toString()

    init {
        viewModelScope.launch {
            // Restore previous session if exists to keep local memory context
            sessionStore.activeSessionIdFlow.collectLatest { storedId ->
                if (storedId != null) {
                    sessionId = storedId
                } else {
                    sessionId = UUID.randomUUID().toString()
                    sessionStore.saveActiveSessionId(sessionId)
                }
                savedStateHandle[KEY_SESSION_ID] = sessionId
                _uiState.update { it.copy(sessionId = sessionId) }
                
                loadMessages()
            }
        }

        observePendingApproval()
        observeSharedInput()
        observeThermalWarning()
        observeEngineState()
    }

    private fun observeEngineState() {
        viewModelScope.launch {
            modelRunner.loadState.collectLatest { state ->
                _uiState.update { it.copy(engineState = state) }
            }
        }
    }

    fun setSharedInput(input: com.localfriday.app.platform.share.SharedInput?) {
        _uiState.update { it.copy(sharedInput = input) }
    }

    fun warmUpEngine() {
        viewModelScope.launch {
            modelRunner.warmUp()
        }
    }

    private fun observeThermalWarning() {
        viewModelScope.launch {
            runtimeMetricsCollector.thermalWarning.collectLatest { warning ->
                val warningMessage = when (warning) {
                    is com.localfriday.app.core.common.AppError.TemperatureCritical -> "발열이 심하여 기기 보호를 위해 성능이 제한됩니다."
                    is com.localfriday.app.core.common.AppError.TemperatureWarning -> "발열로 인해 추론이 약간 지연될 수 있습니다."
                    else -> null
                }
                _uiState.update { it.copy(warningMessage = warningMessage) }
            }
        }
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
                    state.copy(messages = result.data.toImmutableList()) 
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isInFlight) return

        var imageBytes: ByteArray? = null
        var documentText: String? = null

        val currentSharedInput = _uiState.value.sharedInput
        if (currentSharedInput is com.localfriday.app.platform.share.SharedInput.Image) {
            try {
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(context.contentResolver, currentSharedInput.uri)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, currentSharedInput.uri)
                }
                
                // Re-encode to standard JPEG to prevent "unknown image type" error in LiteRT
                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
                imageBytes = outputStream.toByteArray()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (currentSharedInput is com.localfriday.app.platform.share.SharedInput.Document) {
            documentText = "첨부된 문서 내용(${currentSharedInput.fileName}):\n${currentSharedInput.textContent}"
        }

        clearSharedInput()

        // 낙관적 업데이트 (Optimistic Append)
        val tempUserMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = ChatMessage.Role.USER,
            content = text, // UI에서는 원본 text 노출
            inputType = if (imageBytes != null) InputType.IMAGE else InputType.TEXT,
            createdAt = System.currentTimeMillis()
        )
        
        _uiState.update { state -> 
            state.copy(
                messages = (state.messages + tempUserMessage).toImmutableList(),
                isInFlight = true,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                // 스트리밍 시작 전 빈 문자열로 초기화
                _uiState.update { it.copy(streamingText = "") }
                
                val result = sendChatMessageUseCase(
                    sessionId = sessionId, 
                    message = text, // Send original text
                    imageBytes = imageBytes,
                    documentText = documentText,
                    onToken = { token ->
                        _uiState.update { state ->
                            val currentText = state.streamingText ?: ""
                            state.copy(streamingText = currentText + token)
                        }
                    }
                )
                handleAgentResult(result)
            } finally {
                _uiState.update { it.copy(isInFlight = false, streamingText = null) }
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
                        _uiState.update { it.copy(messages = (it.messages + assistantMessage).toImmutableList()) }
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
            state.copy(messages = (state.messages + sysMessage).toImmutableList())
        }
    }

    companion object {
        private const val KEY_SESSION_ID = "chat_session_id"
    }
}
