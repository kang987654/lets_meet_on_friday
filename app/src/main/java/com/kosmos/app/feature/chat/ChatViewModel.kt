package com.kosmos.app.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.usecase.SendChatMessageUseCase
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

import com.kosmos.app.domain.usecase.ResumeActionUseCase
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
 * 1. UI에서 사용자 입력(텍스트/이미지/음성) 수신 시 [SendChatMessageUseCase] 호출
 * 2. 모델 추론 결과를 받아 상태 업데이트 및 말풍선 UI 리렌더링
 * 3. 에이전트의 승인 요청이 있을 경우 [ApprovalCoordinator]를 통해 UI 다이얼로그 표시
 * 4. 음성 인식(STT) 및 텍스트 음성 변환(TTS) 상태 관리
 */
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
    private val modelRunner: ModelRunner,
    private val audioRecorder: com.kosmos.app.platform.speech.AudioRecorder,
    private val getTodayScheduleUseCase: com.kosmos.app.domain.usecase.GetTodayScheduleUseCase,
    private val addScheduleUseCase: com.kosmos.app.domain.usecase.AddScheduleUseCase
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

    fun setSharedInput(input: com.kosmos.app.platform.share.SharedInput?) {
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

    fun sendMessage(text: String, audioFilePath: String? = null) {
        if ((text.isBlank() && audioFilePath == null) || _uiState.value.isInFlight) return

        var imageBytes: ByteArray? = null
        var documentText: String? = null

        val currentSharedInput = _uiState.value.sharedInput
        if (currentSharedInput is com.kosmos.app.platform.share.SharedInput.Image) {
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
        } else if (currentSharedInput is com.kosmos.app.platform.share.SharedInput.Document) {
            documentText = "첨부된 문서 내용(${currentSharedInput.fileName}):\n${currentSharedInput.textContent}"
        }

        clearSharedInput()

        // 낙관적 업데이트 (Optimistic Append)
        val tempUserMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = ChatMessage.Role.USER,
            content = if (audioFilePath != null && text.isBlank()) "(음성 메시지)" else text,
            inputType = if (audioFilePath != null) InputType.VOICE else if (imageBytes != null) InputType.IMAGE else InputType.TEXT,
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
                _uiState.update { it.copy(streamingText = "", streamingThinking = null) }
                var accumulatedRaw = ""
                var toolCallDetected: com.kosmos.app.assistant.context.ToolParser.ToolCallData? = null
                
                val result = sendChatMessageUseCase(
                    sessionId = sessionId, 
                    message = text, // Send original text
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
                        
                        if (parsed.toolCalls.isNotEmpty() && toolCallDetected == null) {
                            toolCallDetected = parsed.toolCalls.first()
                            viewModelScope.launch { modelRunner.cancel() }
                        }
                    }
                )
                
                if (toolCallDetected != null) {
                    val call = toolCallDetected!!
                    val responseJson = executeTool(call)
                    val toolResponsePrompt = "<tool_response>$responseJson</tool_response>"
                    
                    // Reset accumulators for the second LLM generation
                    accumulatedRaw = ""
                    _uiState.update { it.copy(streamingText = "", streamingThinking = null) }
                    
                    val secondResult = sendChatMessageUseCase(
                        sessionId = sessionId,
                        message = toolResponsePrompt,
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
                    handleAgentResult(secondResult)
                } else {
                    handleAgentResult(result)
                }
            } finally {
                _uiState.update { it.copy(isInFlight = false, streamingText = null, streamingThinking = null) }
            }
        }
    }

    private suspend fun executeTool(call: com.kosmos.app.assistant.context.ToolParser.ToolCallData): String {
        return when (call.name) {
            "AddSchedule" -> {
                val title = call.args["title"] as? String ?: "Event"
                val startTime = call.args["startTime"] as? String ?: ""
                val endTime = call.args["endTime"] as? String ?: ""
                val desc = call.args["description"] as? String
                val res = addScheduleUseCase(title, startTime, endTime, desc)
                if (res is AppResult.Success) {
                    "{\"status\": \"success\", \"message\": \"일정이 성공적으로 추가되었습니다.\"}"
                } else {
                    "{\"status\": \"error\", \"message\": \"일정 추가 실패\"}"
                }
            }
            "GetSchedule" -> {
                val range = if ((call.args["date"] as? String)?.lowercase()?.contains("week") == true) {
                    com.kosmos.app.domain.model.ScheduleData.RangeType.WEEK
                } else {
                    com.kosmos.app.domain.model.ScheduleData.RangeType.TODAY
                }
                val res = getTodayScheduleUseCase(range)
                if (res is AppResult.Success) {
                    val data = res.data
                    val text = buildString {
                        append(data.summary ?: "일정 요약이 없습니다.")
                        append("\\n\\n")
                        data.events.forEach { event ->
                            append("- ${event.title} (${event.startIso})\\n")
                        }
                    }
                    "{\"status\": \"success\", \"data\": \"$text\"}"
                } else {
                    "{\"status\": \"error\", \"message\": \"일정 조회 실패\"}"
                }
            }
            else -> "{\"status\": \"error\", \"message\": \"알 수 없는 Tool입니다.\"}"
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
                            createdAt = System.currentTimeMillis(),
                            thinkingProcess = agentResult.thinkingProcess
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

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            _uiState.update { it.copy(isRecording = false) }
            val result = audioRecorder.stopRecording()
            if (result.isSuccess) {
                val file = result.getOrNull()
                if (file != null && file.exists()) {
                    sendMessage("", file.absolutePath)
                }
            } else {
                _uiState.update { it.copy(error = com.kosmos.app.core.common.AppError.SttError(result.exceptionOrNull()?.message ?: "녹음 중지 실패")) }
            }
        } else {
            val result = audioRecorder.startRecording()
            if (result.isSuccess) {
                _uiState.update { it.copy(isRecording = true) }
            } else {
                _uiState.update { it.copy(error = com.kosmos.app.core.common.AppError.SttError(result.exceptionOrNull()?.message ?: "녹음 시작 실패")) }
            }
        }
    }

    companion object {
        private const val KEY_SESSION_ID = "chat_session_id"
    }
}
