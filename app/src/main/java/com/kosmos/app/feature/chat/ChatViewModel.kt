package com.kosmos.app.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.assistant.approval.ApprovalRequest
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.usecase.SendChatMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList
import java.util.UUID
import javax.inject.Inject

import com.kosmos.app.platform.share.ShareIntentHandler
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import com.kosmos.app.domain.modelrunner.ModelRunner

/**
 * [ChatViewModel]
 * 채팅 화면(UI)의 상태 관리 및 사용자 입력 이벤트를 도메인 레이어(UseCase)로 연결하는 ViewModel입니다.
 *
 * ### Architecture Context
 * - **Layer**: UI (Presentation)
 * - **Dependencies**: [SendChatMessageUseCase], [ResumeActionUseCase], [ApprovalCoordinator], [SessionStore], [ConversationRepository]
 *
 * ### Key Flow
 * 1. 사용자 텍스트/음성/이미지 입력을 받아 로컬 상태 업데이트 (isInFlight = true)
 * 2. [SendChatMessageUseCase] 호출 및 스트리밍 토큰 수신 시 UI 실시간 반영
 * 3. [AssistantOrchestrator]의 최종 결과(`AgentResult`)에 따라 일반 텍스트, 승인 대기, 에러 상태 처리
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val sessionStore: SessionStore,
    private val conversationRepository: ConversationRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val approvalCoordinator: ApprovalCoordinator,
    private val shareIntentHandler: ShareIntentHandler,
    private val runtimeMetricsCollector: RuntimeMetricsCollector,
    private val modelRunner: ModelRunner,
    private val audioRecorder: com.kosmos.app.platform.speech.AudioRecorder
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var sessionId: String = UUID.randomUUID().toString()

    init {
        viewModelScope.launch {
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

    fun setSharedInput(input: com.kosmos.app.platform.share.SharedInput?) {
        _uiState.update { it.copy(sharedInput = input) }
    }

    private fun observeThermalWarning() {
        viewModelScope.launch {
            runtimeMetricsCollector.thermalWarning.collectLatest { warning ->
                val warningMessage = when (warning) {
                    is com.kosmos.app.core.common.AppError.TemperatureCritical -> "발열이 심하여 기기 보호를 위해 성능이 제한됩니다."
                    is com.kosmos.app.core.common.AppError.TemperatureWarning -> "발열로 인해 추론이 약간 지연될 수 있습니다."
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
                // [WHY] replay=1 캐시를 소비 직후 비워 재구독(화면 복귀) 시 중복 처리를 방지한다.
                shareIntentHandler.clearConsumed()
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

    private fun extractImageBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun sendMessage(text: String, audioFilePath: String? = null) {
        if ((text.isBlank() && audioFilePath == null) || _uiState.value.isInFlight) return

        val currentSharedInput = _uiState.value.sharedInput
        val isImageAttached = currentSharedInput is com.kosmos.app.platform.share.SharedInput.Image
        val documentText = (currentSharedInput as? com.kosmos.app.platform.share.SharedInput.Document)?.let {
            "첨부된 문서 내용(${it.fileName}):\n${it.textContent}"
        }

        clearSharedInput()

        val tempUserMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = ChatMessage.Role.USER,
            content = if (audioFilePath != null && text.isBlank()) "(음성 메시지)" else text,
            inputType = if (audioFilePath != null) InputType.VOICE else if (isImageAttached) InputType.IMAGE else InputType.TEXT,
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
                // [WHY] 최대 10MB 이미지 전체 읽기는 메인 스레드 ANR 위험이 있어 IO에서 수행한다.
                val imageBytes = if (currentSharedInput is com.kosmos.app.platform.share.SharedInput.Image) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        extractImageBytes(currentSharedInput.uri)
                    }
                } else null

                _uiState.update { it.copy(streamingText = "", streamingThinking = null) }
                var accumulatedRaw = ""

                val result = sendChatMessageUseCase(
                    sessionId = sessionId, 
                    message = text,
                    imageBytes = imageBytes,
                    documentText = documentText,
                    audioFilePath = audioFilePath,
                    onToken = { token ->
                        accumulatedRaw += token
                        val parsed = com.kosmos.app.assistant.context.ToolParser.parseStream(accumulatedRaw)
                        _uiState.update { state ->
                            state.copy(
                                streamingText = parsed.content,
                                streamingThinking = parsed.thinking
                            )
                        }
                    }
                )
                
                handleAgentResult(result)
            } finally {
                _uiState.update { it.copy(isInFlight = false, streamingText = null, streamingThinking = null) }
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
        approvalCoordinator.consumePending() ?: return
        approvalCoordinator.approve()
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
                            createdAt = System.currentTimeMillis(),
                            thinkingProcess = agentResult.thinkingProcess
                        )
                        _uiState.update { it.copy(messages = (it.messages + assistantMessage).toImmutableList()) }
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
        approvalCoordinator.consumePending() ?: return
        approvalCoordinator.reject()
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            _uiState.update { it.copy(isRecording = false) }
            viewModelScope.launch {
                val result = audioRecorder.stopRecording()
                if (result is com.kosmos.app.core.common.AppResult.Success) {
                    val file = result.data
                    if (file.exists()) {
                        sendMessage("", file.absolutePath)
                    }
                } else if (result is com.kosmos.app.core.common.AppResult.Failure) {
                    _uiState.update { it.copy(error = result.error) }
                }
            }
        } else {
            val result = audioRecorder.startRecording()
            if (result is com.kosmos.app.core.common.AppResult.Success) {
                _uiState.update { it.copy(isRecording = true) }
            } else if (result is com.kosmos.app.core.common.AppResult.Failure) {
                _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    companion object {
        private const val KEY_SESSION_ID = "chat_session_id"
    }
}
