package com.kosmos.app.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
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
            // [WHY] 이전에는 limit 인자 없이 호출해 계약 기본값(5)이 적용됐고, 채팅을 열면
            // 마지막 5개 메시지만 보였다 — 그 이상은 DB 에 있는데도 화면에서 사라졌다.
            val result = conversationRepository.getRecentBySession(
                sessionId,
                Constants.MAX_RECENT_CONVERSATIONS
            )
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

                // [WHY] 빈 문자열이 아니라 null 이다 — ChatScreen 은 streamingText 가 null 일 때만
                // 타이핑 인디케이터를 띄우므로, ""로 초기화하면 첫 토큰이 오기 전까지 빈 버블만
                // 보이고 스피너가 사라진다.
                _uiState.update { it.copy(streamingText = null, streamingThinking = null) }

                val result = sendChatMessageUseCase(
                    sessionId = sessionId, 
                    message = text,
                    imageBytes = imageBytes,
                    documentText = documentText,
                    audioFilePath = audioFilePath,
                    // [WHY] 여기에 누적기도 파서도 없다 — BaseAgent 가 턴 경계를 알고 파싱한
                    // 결과를 넘긴다. 예전에는 이 콜백이 원시 토큰을 직접 누적했고, 툴 루프가
                    // 다음 턴으로 넘어갈 때 비울 신호가 없어서 1턴 문장이 2턴에 이어붙었다
                    // (ADR-007).
                    onStream = { update ->
                        _uiState.update { state ->
                            state.copy(
                                streamingText = update.content,
                                streamingThinking = update.thinking
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

    /**
     * 진행 중인 응답 생성을 중단합니다.
     * [WHY] 온디바이스 추론은 응답이 길어질 수 있어 사용자가 끊을 수단이 필요하다.
     * 코루틴을 취소하면 스트리밍된 부분 응답의 DB 저장까지 함께 날아가므로,
     * 모델 스트림만 중단(cancelProcess)하고 파이프라인은 정상 종료시켜 부분 응답을 보존한다.
     */
    fun cancelGeneration() {
        if (!_uiState.value.isInFlight) return
        viewModelScope.launch {
            modelRunner.cancel()
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
