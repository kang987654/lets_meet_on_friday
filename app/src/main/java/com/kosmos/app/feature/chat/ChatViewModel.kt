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
import androidx.paging.cachedIn
import com.kosmos.app.ui.paging.unwrapForPaging
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
    @param:ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val sessionStore: SessionStore,
    private val conversationRepository: ConversationRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val episodeRepository: com.kosmos.app.domain.memory.EpisodeRepository,
    private val approvalCoordinator: ApprovalCoordinator,
    private val shareIntentHandler: ShareIntentHandler,
    private val runtimeMetricsCollector: RuntimeMetricsCollector,
    private val modelRunner: ModelRunner,
    private val audioRecorder: com.kosmos.app.platform.speech.AudioRecorder
) : ViewModel() {

    // [WHY] 녹음 자동 종료 타이머. 사용자가 먼저 멈추면 취소해야 한다 — 남겨 두면 다음 녹음
    // 도중에 깨어나 남의 녹음을 끊는다.
    private var recordingTimeoutJob: kotlinx.coroutines.Job? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var sessionId: String = UUID.randomUUID().toString()

    /**
     * 타임라인 앵커 — 화면 생성 시각. **앵커 이전은 Paging(불변 집합), 이후는 라이브 테일**
     * ([ChatUiState.messages])이 담당한다.
     *
     * [WHY] offset 페이징에 새 행이 끼어들면 offset 이 밀려 중복/누락이 난다. 앵커로 페이징
     * 집합을 불변으로 만들면 그 결함이 원천 차단되고, 낙관적 append·스트리밍 버블 로직은
     * 한 줄도 바꾸지 않아도 된다 (시안 A′ M2-3 핵심 결정).
     */
    val timelineAnchor: Long = System.currentTimeMillis()

    /** 앵커 이전 과거 대화 — 세션 무관 연속 타임라인 (ADR-022: 세션은 UX 개념이 아니다). */
    val historyPaging: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<ChatMessage>> =
        androidx.paging.Pager(
            androidx.paging.PagingConfig(
                pageSize = Constants.CHAT_TIMELINE_PAGE_SIZE,
                initialLoadSize = Constants.CHAT_TIMELINE_PAGE_SIZE * 3,
                // [WHY] placeholder 가 있어야 에피소드 → 원문 점프(scrollToItem 먼 인덱스)가
                // O(1) 로 된다 (CountedPagingSource [WHY] 참조).
                enablePlaceholders = true
            )
        ) {
            com.kosmos.app.ui.paging.CountedPagingSource(
                count = {
                    conversationRepository.countOlderThan(timelineAnchor).unwrapForPaging()
                },
                fetch = { offset, limit ->
                    conversationRepository.getPagedAll(timelineAnchor, offset, limit).unwrapForPaging()
                }
            )
        }.flow.cachedIn(viewModelScope)

    /**
     * 회수 칩(🧠) 라벨 캐시 — episodeId → "제목 (M월 d일)".
     *
     * [WHY] DB 에는 id 만 저장한다(제목 비정규화 회피 — 사용자가 시트에서 제목을 고치면 낡는다).
     * 라벨은 렌더 시점에 여기서 해석하고, 못 찾으면(삭제된 에피소드) 칩을 그리지 않는다.
     */
    private val _episodeChipLabels = MutableStateFlow<Map<String, String?>>(emptyMap())
    val episodeChipLabels: StateFlow<Map<String, String?>> = _episodeChipLabels.asStateFlow()

    /** [episodeId] 라벨을 캐시에 채웁니다 — 이미 조회했으면(실패 포함) 재조회하지 않습니다. */
    fun ensureEpisodeChipLabel(episodeId: String) {
        if (_episodeChipLabels.value.containsKey(episodeId)) return
        // 동시 재진입 방지 — 먼저 자리를 차지해 두고 결과로 덮는다.
        _episodeChipLabels.update { it + (episodeId to null) }
        viewModelScope.launch {
            val episode = (episodeRepository.getById(episodeId) as? AppResult.Success)?.data
                ?: return@launch
            val date = java.time.Instant.ofEpochMilli(episode.startAt)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("M월 d일", java.util.Locale.KOREAN))
            val label = "${episode.title ?: "지난 대화"} ($date)"
            _episodeChipLabels.update { it + (episodeId to label) }
        }
    }

    /**
     * [startAt] 시각 메시지의 **히스토리(앵커 이전) 내 인덱스**를 계산합니다. 앵커 이후(라이브
     * 테일)에 있으면 null — 화면이 테일에서 직접 찾는다.
     *
     * [WHY] countNewerThan 은 전체 행 기준이라 앵커 이후 행도 세는데, 그 몫은 라이브 테일이
     * 화면에서 따로 차지한다. 두 카운트의 차로 히스토리 몫만 남기면 낙관적 메시지의 영속
     * 여부와 무관하게 정확하다. placeholder 덕에 scrollToItem 은 O(1) 이다 (CountedPagingSource).
     */
    suspend fun historyIndexOf(startAt: Long): Int? {
        val total = (conversationRepository.countNewerThan(startAt) as? AppResult.Success)?.data
            ?: return null
        val afterAnchor = (conversationRepository.countNewerThan(timelineAnchor) as? AppResult.Success)?.data
            ?: return null
        val inHistory = total - afterAnchor
        return if (inHistory <= 0) null else inHistory - 1
    }

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
        observeDeviceStatus()
    }

    /**
     * 상단 상태 표시용 값을 주기적으로 갱신합니다.
     *
     * [WHY] 추론 중에는 2초, 유휴에는 5초다. `Debug.getMemoryInfo` 가 수십 ms 이므로 항상 2초로
     * 돌 이유가 없고, 반대로 생성 중에는 tok/s 와 발열이 실제로 움직이므로 촘촘해야 의미가 있다.
     * `viewModelScope` 라 화면이 사라지면 함께 멈춘다.
     */
    private fun observeDeviceStatus() {
        viewModelScope.launch {
            runtimeMetricsCollector.deviceStatus.collectLatest { status ->
                _uiState.update { it.copy(deviceStatus = status) }
            }
        }
        viewModelScope.launch {
            while (true) {
                runtimeMetricsCollector.refreshDeviceStatus()
                kotlinx.coroutines.delay(
                    if (_uiState.value.isInFlight) STATUS_REFRESH_BUSY_MS else STATUS_REFRESH_IDLE_MS
                )
            }
        }
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

    /**
     * 라이브 테일(앵커 이후 메시지)을 DB 진실로 재동기화합니다.
     *
     * [WHY] 예전 loadMessages 는 최근 150개를 전량 로드했다 — 이제 앵커 이전은 [historyPaging]
     * 이 담당하므로, 이 함수는 **이 세션에서 생긴 것들**(음성 전사 교체 등)만 되읽는다.
     * 전량 로드 시절의 실패 안내([WHY] Failure 분기)는 그대로 유지한다.
     */
    private fun loadMessages() {
        viewModelScope.launch {
            val result = conversationRepository.getRecentBySession(
                sessionId,
                Constants.CHAT_TIMELINE_PAGE_SIZE
            )
            when (result) {
                is AppResult.Success -> _uiState.update { state ->
                    state.copy(
                        messages = result.data
                            .filter { it.createdAt >= timelineAnchor }
                            .toImmutableList()
                    )
                }
                // [WHY] 예전에는 Failure 분기가 없어 DB 읽기가 실패하면 **아무 안내 없이 빈
                // 화면**으로 열렸다 — 저장된 대화가 있는데도 사라진 것처럼 보인다. 스낵바가
                // "데이터를 불러오지 못했어요" 를 띄우게 오류를 올린다.
                is AppResult.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private fun extractImageBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            // [WHY] printStackTrace 가 아니라 로그다 — 이 파일에서 유일한 규약 위반이었다.
            // null 의 의미(읽기 실패)는 호출자가 턴 중단으로 처리한다.
            android.util.Log.w("ChatViewModel", "첨부 이미지 읽기 실패: ${uri}", e)
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

                // [WHY] 이미지 첨부 턴인데 읽기가 실패하면 **턴을 중단한다.** 예전에는 null 이면
                // 이미지 없이 그대로 진행했다 — 말풍선은 📷 "첨부된 이미지" 를 표시하는데 모델은
                // 텍스트만 받아, 사용자는 모델이 이미지를 보고 답했다고 오해했다(성공처럼 보이는
                // 실패). 낙관적 말풍선을 걷어내고 오류를 알린다.
                if (currentSharedInput is com.kosmos.app.platform.share.SharedInput.Image && imageBytes == null) {
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.filterNot { it.id == tempUserMessage.id }.toImmutableList(),
                            error = com.kosmos.app.core.common.AppError.UnsupportedImageFormat("첨부 이미지를 읽지 못했습니다")
                        )
                    }
                    return@launch
                }

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
                // [WHY] 음성 턴의 사용자 말풍선은 전사가 끝나기 전에 "(음성 메시지)" 자리표시자로
                // 띄운 낙관적 항목이다. 턴이 끝나면 DB 의 진실(전사문)로 교체한다 — 교체하지
                // 않으면 그 세션 화면에서 전사 결과를 확인할 방법이 없다(2026-08-14 실기기 관측:
                // 자리표시자가 세션 내내 남았다). 전사 실패 턴은 사용자 메시지를 저장하지
                // 않으므로(0.12.0) 자리표시자도 함께 사라지고, 오류 안내는 handleAgentResult 몫이다.
                if (audioFilePath != null) loadMessages()
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

    fun dismissSearchFailedNotice() {
        _uiState.update { it.copy(searchFailedNotice = false) }
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
                            thinkingProcess = agentResult.thinkingProcess,
                            searchUsed = agentResult.searchUsed,
                            // [WHY] 회수 칩(🧠) 데이터 — DB 에는 BaseAgent 가 이미 저장했고,
                            // 화면의 낙관적 메시지에도 실어야 재로드 없이 칩이 보인다 (M2-5 렌더).
                            recallEpisodeIds = agentResult.recallEpisodeIds
                        )
                        _uiState.update {
                            it.copy(
                                messages = (it.messages + assistantMessage).toImmutableList(),
                                // [WHY] 검색이 허용됐는데 실패한 턴이다. 알리지 않으면 사용자가
                                // 온디바이스 답변을 검색 결과로 오해한다 (PRD V1-AC3·EC5).
                                searchFailedNotice = agentResult.searchFailed,
                                // [WHY] 응답의 DB 저장이 실패한 턴 — 화면에는 있지만 재시작하면
                                // 사라진다. "저장에 실패했어요" 스낵바로 알린다.
                                error = if (agentResult.persistFailed) {
                                    com.kosmos.app.core.common.AppError.DbWriteError("conversation")
                                } else it.error
                            )
                        }
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

    /**
     * 마이크 버튼. 녹음을 시작하거나, 멈추고 전사 턴을 보냅니다.
     *
     * [WHY] 시작할 때 **자동 종료 타이머**를 함께 건다. Gemma 4 공식 문서가 오디오를 최대 30초로
     * 제한하는데 예전에는 상한이 없어 버튼을 다시 누를 때까지 무한히 녹음됐다 — 사용자가 1분을
     * 말하면 한계를 넘긴 오디오가 그대로 모델에 들어가고 그때의 동작은 정의되지 않았다.
     *
     * [WHY] 상한에 닿으면 **버린 채로 멈추지 않고 그때까지의 녹음을 그대로 보낸다.** 30초를 말한
     * 사용자에게 아무 응답도 주지 않는 것이 더 나쁜 실패다. `AudioRecorder` 가 파일 자체를
     * 30초에서 끊으므로 모델이 받는 것은 항상 상한 이내다.
     */
    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecordingAndSend()
        } else {
            val result = audioRecorder.startRecording()
            if (result is com.kosmos.app.core.common.AppResult.Success) {
                _uiState.update { it.copy(isRecording = true) }
                recordingTimeoutJob = viewModelScope.launch {
                    kotlinx.coroutines.delay(Constants.MAX_AUDIO_SECONDS * 1000L)
                    if (_uiState.value.isRecording) stopRecordingAndSend()
                }
            } else if (result is com.kosmos.app.core.common.AppResult.Failure) {
                _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private fun stopRecordingAndSend() {
        // [WHY] 사용자가 먼저 멈추면 타이머를 취소한다 — 남겨 두면 다음 녹음 도중에 깨어나
        // **남의 녹음을 끊는다.**
        recordingTimeoutJob?.cancel()
        recordingTimeoutJob = null
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
    }

    companion object {
        private const val KEY_SESSION_ID = "chat_session_id"
        private const val STATUS_REFRESH_BUSY_MS = 2_000L
        private const val STATUS_REFRESH_IDLE_MS = 5_000L
    }
}
