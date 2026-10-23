package com.kosmos.app.feature.chat

import androidx.compose.ui.res.painterResource
import com.kosmos.app.ui.theme.KosmosTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import com.kosmos.app.ui.theme.scaled
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.ui.component.kosmosSurface
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
// [WHY] ChatScreen.kt(1,200여 줄, 12개 컴포저블 동거)를 응집 단위로 분리했다
// (2026-08-15, MVP 감사 refactor-ui) — 순수 이동이며 동작 변경이 없다. 이 파일: 화면 조립(ChatScreen)과 목록 행 모델(ChatRow). 파일 헤더 KDoc 2벌 중 낡은 쪽은 삭제했다.
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import com.kosmos.app.core.mapper.ErrorMessages
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.security.PermissionPolicy
import com.kosmos.app.platform.notification.NotificationAccess

/**
 * 메인 채팅 화면 (ChatScreen)
 *
 * **Core Role**: 사용자(User)와 AI 어시스턴트(KOSMOS) 간의 실시간 대화 인터페이스를 제공하는 메인 스크린입니다.
 * 텍스트, 음성(마이크), 이미지/문서 첨부 등 멀티모달 입력을 지원하며, AI의 스트리밍 응답과 추론 상태(Thinking Process)를 시각적으로 렌더링합니다.
 *
 * **Architecture Context**:
 * - Layer: UI (Presentation / Feature Layer)
 * - Dependencies: `ChatViewModel` (상태 관리 및 비즈니스 로직 연동), `CustomChatHeader`, `ChatInputBar` 등 하위 UI 컴포넌트
 *
 * **Key Flow**:
 * 1. [사용자 입력] 하단 `ChatInputBar`를 통해 텍스트/음성/파일 입력 이벤트 발생 -> `ChatViewModel`로 전달
 * 2. [추론 및 상태 갱신] 뷰모델에서 모델 추론이 진행되는 동안 `uiState.isInFlight` 및 `uiState.messages` 갱신
 * 3. [UI 렌더링] LazyColumn을 통해 채팅 기록(`ChatBubbleUser`, `ChatBubbleAssistant`) 및 모델의 Thinking 과정이 애니메이션과 함께 렌더링됨
 * 4. [스타일링] Figma 스펙(Phase 2)에 맞춘 Glassmorphism 기반의 유려한 컴포넌트(비대칭 모서리, 그라데이션)로 사용자 경험 극대화
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    // [WHY] onSettingsClick 은 제거됐다 — 설정 진입은 드로어 타일(M2-2). ☰ 은 셸 층의
    // drawerState.open 을 람다로 받는다. 기본값은 E2E 계약(단독 compose) 때문에 필수.
    onMenuClick: () -> Unit = {},
    webSearchEnabled: Boolean = false,
    onToggleWebSearch: (Boolean) -> Unit = {},
    // [WHY] 드로어의 에피소드 시트 → "원문 대화 보기" (M2-5). 셸(MainScreen)이 요청 시각을
    // 내려주고, 소비 후 [onJumpConsumed]로 지운다. 기본값은 E2E 계약(단독 compose) 때문에 필수.
    jumpToTimestamp: Long? = null,
    onJumpConsumed: () -> Unit = {},
    // [WHY] 결과 카드·요약 타일(0.38.2 M3) — 셸이 ChatExtrasViewModel 로 채운다. 기본값은 E2E 계약(단독 compose): 카드·타일 없이 글만.
    resultStates: Map<String, com.kosmos.app.feature.chat.result.ResultCardState> = emptyMap(),
    onEnsureResult: (com.kosmos.app.domain.model.MessageResult) -> Unit = {},
    onResultClick: (ChatMessage) -> Unit = {},
    summary: com.kosmos.app.feature.chat.result.ChatSummary? = null,
    onRefreshSummary: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    // [WHY] 결과 카드가 여는 시트(0.38.2 M4)를 셸이 채운다 — 기본값은 E2E 계약(단독 compose).
    overlay: @Composable () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // [WHY] 요약 타일은 대화로 알림·할 일이 생기거나 다른 화면에서 고친 뒤 돌아올 때 바뀐다 — 그때 다시 센다.
    LaunchedEffect(uiState.messages.size) { onRefreshSummary() }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        onRefreshSummary()
        onPauseOrDispose { }
    }
    val speakingMessageId by viewModel.speakingMessageId.collectAsStateWithLifecycle()
    val attachmentExtracting by viewModel.attachmentExtracting.collectAsStateWithLifecycle()
    var showStatusSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current

    // [WHY] 연속 타임라인 (시안 A′ M2-3): 앵커 이전 과거는 Paging, 이후는 라이브 테일
    // (uiState.messages). reverseLayout 이므로 index 0 = 화면 바닥(최신)이다.
    val history = viewModel.historyPaging.collectAsLazyPagingItems()
    val liveTail = remember(uiState.messages) { uiState.messages.asReversed() }

    // [WHY] 사용자가 위로 스크롤해 과거 대화를 읽는 중이라면 새 토큰이 도착해도 끌어내리지 않는다.
    // 스크롤 제스처가 끝난 시점에 바닥 여부를 기록해 자동 스크롤 여부를 판단한다. (C-3)
    // reverseLayout 에서 "바닥에서 벗어남" = canScrollBackward (index 0 쪽으로 되돌아갈 수 있음).
    var userScrolledAway by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress ->
                if (!inProgress) userScrolledAway = listState.canScrollBackward
            }
    }

    // 새 메시지·스트리밍 시작 시 바닥(최신)으로 — reverseLayout 에서 바닥 = index 0.
    LaunchedEffect(uiState.messages.size, uiState.isInFlight) {
        // [WHY] 위로 올려 둔 채 보내면 새 답을 따라 내려가지 않았다(0.38.2 실기기) — 내가 막 보낸 턴이면 바닥으로 돌아간다.
        if (uiState.isInFlight && uiState.messages.lastOrNull()?.role == ChatMessage.Role.USER) userScrolledAway = false
        if (!userScrolledAway) {
            listState.animateScrollToItem(0)
        }
    }

    // ── 회수 칩 + 원문 점프 (시안 A′ M2-5) ─────────────────────────────────────
    val episodeChipLabels by viewModel.episodeChipLabels.collectAsStateWithLifecycle()
    var openEpisodeId by remember { mutableStateOf<String?>(null) }
    // 점프 도착 지점 1회성 하이라이트 — 값 = 에피소드 startAt, 경계 메시지가 자기를 식별한다.
    var highlightStartAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(highlightStartAt) {
        if (highlightStartAt != null) {
            kotlinx.coroutines.delay(2_500)
            highlightStartAt = null
        }
    }

    /**
     * [startAt] 시각의 메시지로 스크롤한다. reverseLayout 인덱스 = [스트리밍/타이핑 헤더]
     * + 라이브 테일 + 히스토리 순이므로, 히스토리 내 인덱스(ViewModel 계산)에 앞 구간을 더한다.
     * placeholder 덕에 미로드 위치로도 O(1) 점프가 된다 (CountedPagingSource).
     */
    val jumpToTimeline: suspend (Long) -> Unit = { startAt ->
        val headerCount = (if (uiState.streamingText != null || uiState.streamingThinking != null) 1 else 0) +
            (if (uiState.isInFlight && uiState.streamingText == null) 1 else 0)
        val historyIndex = viewModel.historyIndexOf(startAt)
        val target = if (historyIndex != null) {
            headerCount + liveTail.size + historyIndex
        } else {
            // 앵커 이후에 시작한 에피소드 — 라이브 테일에서 가장 오래된 해당 메시지를 찾는다.
            val tailIndex = liveTail.indexOfLast { it.createdAt >= startAt }
            if (tailIndex >= 0) headerCount + tailIndex else 0
        }
        val lastIndex = headerCount + liveTail.size + history.itemCount - 1
        val index = target.coerceIn(0, maxOf(0, lastIndex))
        userScrolledAway = true
        highlightStartAt = startAt
        listState.scrollToItem(index)
        // [WHY] 자리표시자인 채로 정렬하면 내용이 채워지며 높이가 바뀌어 원문 앞부분이 화면 위로 잘린다 — 대상이 로드될 때까지
        // 잠깐 기다린 뒤 다시 잡는다. 시간 안에 안 오면 바닥 정렬 그대로 둔다(이동 자체는 이미 끝났다).
        val pagedIndex = index - headerCount - liveTail.size
        val loaded = pagedIndex < 0 || kotlinx.coroutines.withTimeoutOrNull(JUMP_LOAD_WAIT_MS) {
            androidx.compose.runtime.snapshotFlow { history.peek(pagedIndex) }.first { it != null }
        } != null
        if (loaded) {
            if (pagedIndex >= 0) listState.scrollToItem(index)
            alignItemTop(listState, index)
        }
    }

    LaunchedEffect(jumpToTimestamp) {
        if (jumpToTimestamp != null) {
            jumpToTimeline(jumpToTimestamp)
            onJumpConsumed()
        }
    }


    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    // [WHY] 오류와 토글 피드백을 스낵바 한 곳으로 모으고, 문구는 ErrorMessages로 인간화한다.
    // 첨부 피커와 권한 런처들이 이 두 값을 쓰므로 먼저 선언한다.
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val notify: (String) -> Unit = { message -> snackbarScope.launch { snackbarHostState.showSnackbar(message) } }
    // [WHY] 행마다 새 람다를 만들지 않는다 — 타임라인은 페이징 목록이라 행 수만큼 재할당된다.
    val copyMessage: (String) -> Unit = remember(clipboard, snackbarHostState) {
        { content -> copyToClipboard(snackbarScope, clipboard, snackbarHostState, content) }
    }
    val today = com.kosmos.app.ui.component.rememberToday()

    val attachmentReader = remember(contentResolver) {
        com.kosmos.app.platform.share.AttachmentReader(contentResolver)
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // [WHY] 동기 읽기다 — 큰 읽기를 하지 않는 것이 해법이다(AttachmentReader KDoc).
            val result = attachmentReader.read(uri)
            if (result.documentFile) {
                // 문서 파일은 IO 에서 읽는다 — 끝나면 공유 입력 흐름으로 프리뷰가 채워진다(0.35.0 M4).
                viewModel.attachDocumentFile(uri)
                return@rememberLauncherForActivityResult
            }
            if (result.rejected) {
                notify("이 파일은 글자로 첨부할 수 없어요. PDF, 엑셀, 워드, 한글(hwpx), 텍스트 파일을 첨부해 주세요.")
                return@rememberLauncherForActivityResult
            }
            if (result.truncated) notify("문서가 길어 앞부분만 첨부돼요. 긴 문서 요약은 아직 지원하지 않아요.")
            val shared = result.input
            if (shared != null) {
                viewModel.setSharedInput(shared)
            } else {
                // [WHY] 읽기 실패(예외·null 스트림)를 조용히 넘기면 사용자는 버튼이 고장 났다고 본다.
                notify("첨부 파일을 읽지 못했어요. 다른 파일을 골라 주세요.")
            }
        }
    }

    // [WHY] 거부하면 안내한다 — PRD EC2("음성 입력 비활성 + 텍스트 입력 안내"). 문구는 ErrorMessages 단일 출처.
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleRecording()
        } else {
            notify(ErrorMessages.userMessage(AppError.PermissionDenied(PermissionPolicy.MICROPHONE)))
        }
    }
    // 마이크 버튼과 음성 실행 요청이 같은 권한 게이트를 쓴다.
    val startVoice = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.toggleRecording()
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // [WHY] 일괄 선요청(P4-11 제거) 대신 일정 승인 시점에 컨텍스트와 함께 캘린더 권한을 요청한다.
    // 거부해도 일정은 로컬 DB에 저장되고 기기 캘린더 동기화만 생략된다 (ADR-004 Graceful Degradation).
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // 결과와 무관하게 진행 — 미승인 시 로컬 저장만 수행. 대화상자가 카드를 가린 동안 흐른 승인
        // 제한 시간은 되돌려 준다(ApprovalCoordinator.restartTimeout).
        viewModel.onApprovalObstructionCleared()
    }

    // [WHY] 알림 권한도 같은 방식이다 — 리마인더 승인 카드가 뜰 때 요청한다(0.36.0 D4). 거부해도 리마인더는 저장되고 울리지만 화면에
    // 뜨지 않으므로 그 사실을 알린다.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onApprovalObstructionCleared()
        if (!granted) notify(NotificationAccess.DENIED_MESSAGE)
    }

    LaunchedEffect(uiState.pendingApproval) {
        val pending = uiState.pendingApproval
        val needsCalendarPermission = pending?.calendarDraft != null
        if (needsCalendarPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED
        ) {
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
        val notificationPermission = NotificationAccess.runtimePermission
        if (pending?.showsNotification == true && notificationPermission != null &&
            !NotificationAccess.canPost(context)
        ) {
            notificationPermissionLauncher.launch(notificationPermission)
        }
    }

    // [WHY] 기존에는 uiState.error가 화면에 전혀 표시되지 않아 실패가 무음으로 사라졌다.
    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(ErrorMessages.userMessage(error))
        viewModel.dismissError()
    }

    LaunchedEffect(uiState.searchFailedNotice) {
        if (!uiState.searchFailedNotice) return@LaunchedEffect
        snackbarHostState.showSnackbar("웹 검색 보강에 실패해 기기 안의 정보만으로 답했어요.")
        viewModel.dismissSearchFailedNotice()
    }

    // 프로필 제안 승인/거절 결과 (C′2) — 상한 초과 안내가 여기로 온다.
    LaunchedEffect(uiState.suggestionNotice) {
        val notice = uiState.suggestionNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice)
        viewModel.dismissSuggestionNotice()
    }

    // [WHY] 위젯 🎤·QS 타일 진입 — "도착 즉시 녹음 시작" (A3, 사용자 결정). 소비를 시작 시도
    // 앞에 둬 재구성 시 중복 발화를 막고, 이미 녹음·생성 중이면 조용히 접는다(마이크 버튼의
    // enabled 조건과 동일). 권한 게이트는 마이크 버튼과 같은 런처를 재사용한다.
    LaunchedEffect(uiState.pendingVoiceStart) {
        if (!uiState.pendingVoiceStart) return@LaunchedEffect
        viewModel.consumePendingVoiceStart()
        if (uiState.isRecording || uiState.isInFlight) return@LaunchedEffect
        startVoice()
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                CustomChatHeader(
                    onMenuClick = onMenuClick,
                    engineState = uiState.engineState,
                    deviceStatus = uiState.deviceStatus,
                    warningMessage = uiState.warningMessage,
                    onStatusClick = { showStatusSheet = true }
                )
                summary?.let { SummaryTiles(it, onOpenTasks = onOpenTasks, onOpenCalendar = onOpenCalendar) }
            }
        },
        bottomBar = {
            val recordingLevel by viewModel.recordingLevel.collectAsStateWithLifecycle()
            ChatInputBar(
                isLoading = uiState.isInFlight,
                isRecording = uiState.isRecording,
                recordingLevel = { recordingLevel },
                sharedInput = uiState.sharedInput,
                onClearSharedInput = { viewModel.clearSharedInput() },
                attachmentExtracting = attachmentExtracting,
                onSend = { text ->
                    if (text.isNotBlank()) {
                        viewModel.sendMessage(text)
                    }
                },
                onMicClick = startVoice,
                onAttachClick = { imagePickerLauncher.launch("*/*") },
                onStopGeneration = { viewModel.cancelGeneration() },
                webSearchEnabled = webSearchEnabled,
                onToggleWebSearch = { enabled ->
                    onToggleWebSearch(enabled)
                    notify(if (enabled) "웹 검색을 켰어요. 위키백과를 찾아볼 수 있어요." else "웹 검색을 껐어요. 기기 안에서만 답해요.")
                }
            )
        }
    ) { innerPadding ->
        // [WHY] 칩은 마지막 비서 답에만 — 새 턴이 시작되면(사용자 메시지·응답 대기) 사라진다(0.38.2 M6).
        val lastMessageId = (liveTail.firstOrNull() ?: if (history.itemCount > 0) history.peek(0) else null)?.id
        val chipsFor: (ChatMessage) -> List<com.kosmos.app.feature.chat.result.ChatChip> = { message ->
            if (message.id == lastMessageId && !uiState.isInFlight && uiState.streamingText == null) {
                com.kosmos.app.feature.chat.result.ChatChipRules.of(message)
            } else emptyList()
        }
        val timelineRow: @Composable (ChatMessage, ChatMessage?) -> Unit = { message, older ->
            TimelineRow(
                message = message,
                older = older,
                today = today,
                episodeChipLabels = episodeChipLabels,
                highlightStartAt = highlightStartAt,
                onEnsureChipLabel = viewModel::ensureEpisodeChipLabel,
                onOpenEpisode = { openEpisodeId = it },
                onCopy = copyMessage,
                speakingMessageId = speakingMessageId,
                onToggleSpeak = viewModel::toggleSpeak,
                resultStates = resultStates,
                onEnsureResult = onEnsureResult,
                onResultClick = onResultClick,
                chips = chipsFor(message),
                onChipClick = { chip ->
                    when (val action = chip.action) {
                        is com.kosmos.app.feature.chat.result.ChatChip.Action.Send -> viewModel.sendMessage(action.text)
                        is com.kosmos.app.feature.chat.result.ChatChip.Action.OpenEpisode -> openEpisodeId = action.episodeId
                    }
                }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // [WHY] 채팅 글자 크기 설정(0.38.2)은 목록 안에만 — 상단 바·타일·입력바는 화면 틀이라 그대로다.
                val chatTypography = MaterialTheme.typography.scaled(com.kosmos.app.ui.theme.LocalChatTextScale.current)
                MaterialTheme(typography = chatTypography) {
                    // [WHY] reverseLayout — 선언 순서대로 index 0(바닥)부터 위로 쌓인다:
                    // 스트리밍/타이핑 → 라이브 테일(최신순) → 페이징된 과거(최신순 DESC 그대로).
                    // 과거로 스크롤하면 Paging 이 다음 페이지를 로드한다.
                    // [WHY] 대화 기록이 하나도 없으면(첫 실행·초기화 후) 첫 화면(EmptyChat)을 띄운다(0.38.2 M7b).
                    val showEmpty = liveTail.isEmpty() && history.itemCount == 0 &&
                        history.loadState.refresh is androidx.paging.LoadState.NotLoading &&
                        !uiState.isInFlight && uiState.streamingText == null
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (uiState.streamingText != null || uiState.streamingThinking != null) {
                            item(key = "streaming") {
                                ChatBubbleAssistant(text = uiState.streamingText ?: "", thinkingProcess = uiState.streamingThinking)
                            }
                        }

                        if (uiState.isInFlight && uiState.streamingText == null) {
                            item(key = "typing") {
                                TypingIndicator()
                            }
                        }

                        // 라이브 테일 — 이 세션에서 생긴 메시지 (최신이 index 앞).
                        // [WHY] key가 없으면 스트리밍 append마다 전체 행이 리바인드되고
                        // 아코디언 확장 상태가 엉뚱한 버블에 붙을 수 있다.
                        items(liveTail.size, key = { liveTail[it].id }) { index ->
                            val message = liveTail[index]
                            // 다음(더 과거) 이웃: 테일 내부 → 없으면 페이징의 첫 항목.
                            val older = liveTail.getOrNull(index + 1)
                                ?: if (history.itemCount > 0) history.peek(0) else null
                            timelineRow(message, older)
                        }

                        // 페이징된 과거 — placeholder(null)는 스켈레톤으로.
                        items(
                            count = history.itemCount,
                            key = history.itemKey { it.id }
                        ) { index ->
                            val message = history[index]
                            if (message == null) {
                                PlaceholderBubble()
                            } else {
                                val older = if (index + 1 < history.itemCount) history.peek(index + 1) else null
                                timelineRow(message, older)
                            }
                        }
                    }
                    if (showEmpty) {
                        EmptyChat(onStarter = viewModel::sendMessage, modifier = Modifier.align(Alignment.Center))
                    }
                    }
                }
            }

            // 최신으로 이동 FAB — 위로 스크롤한 상태에서만 노출 (C-3).
            // reverseLayout 에서 "위로 스크롤함" = canScrollBackward, 최신 = index 0.
            if (listState.canScrollBackward) {
                // [WHY] 그림자 없는 원형 + hairline — DESIGN Elevation "그림자를 쓰지 않는다"(impeccable 점검 P2).
                IconButton(
                    onClick = {
                        userScrolledAway = false
                        snackbarScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .size(48.dp)
                        .kosmosSurface(shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = com.kosmos.app.R.drawable.ic_ph_caret_down),
                        contentDescription = "최신 메시지로",
                        tint = KosmosTheme.colors.brandText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

        }
    }

    // [WHY] 승인은 일정·알림·메모·프로필 모두 공통 승인 시트 하나다(0.38.2 M5) — 떠 있는 카드는 대화를 가렸다. 툴 승인이 대기 중이면
    // 프로필 제안은 양보하고(이중 노출 금지), 답을 쓰는 중에는 띄우지 않는다. 끌어내린 제안은 이 화면에서만 접는다(테이블에 남는다).
    val request = uiState.pendingApproval
    if (request != null) {
        val inexact = request.showsNotification &&
            !NotificationAccess.canScheduleExact(context)
        com.kosmos.app.feature.approval.ApprovalSheet(
            request = request,
            onApprove = { viewModel.approvePendingRequest() },
            onReject = { viewModel.rejectPendingRequest() },
            footnote = if (inexact) NotificationAccess.INEXACT_NOTE else null,
            timeoutKey = uiState.approvalTimeoutEpoch,
            onFootnoteAction = if (inexact) {
                {
                    runCatching { context.startActivity(NotificationAccess.exactAlarmSettingsIntent(context)) }
                    // 설정 화면에 다녀오는 동안 시간이 흐르지 않게 제한 시간을 새로 준다(권한 대화상자와 같은 경로).
                    viewModel.onApprovalObstructionCleared()
                }
            } else null
        )
    }
    // [WHY] "나중에"는 그때 보인 제안 전부를 접는다 — 새 제안이 생기면 다시 뜬다. 저장 가능한 값이라 id 를 줄로 이어 둔다.
    var laterSuggestionIds by rememberSaveable { mutableStateOf("") }
    val suggestions = uiState.pendingSuggestions
    if (request == null && !uiState.isInFlight && suggestions.any { it.id !in laterSuggestionIds.split('\n') }) {
        com.kosmos.app.feature.approval.ProfileSuggestionSheet(
            suggestions = suggestions,
            onSave = { viewModel.resolveSuggestions(suggestions, it) },
            onRejectAll = { viewModel.resolveSuggestions(suggestions, emptySet()) },
            onLater = { laterSuggestionIds = suggestions.joinToString("\n") { it.id } }
        )
    }

    overlay()

    if (showStatusSheet) {
        StatusDetailSheet(
            engineState = uiState.engineState,
            status = uiState.deviceStatus,
            warningMessage = uiState.warningMessage,
            onDismiss = { showStatusSheet = false }
        )
    }

    // 회수 칩 탭 → 에피소드 시트 — 드로어와 같은 시트를 재사용한다 (EpisodeSheet [WHY]).
    val chipEpisodeId = openEpisodeId
    if (chipEpisodeId != null) {
        com.kosmos.app.feature.episode.EpisodeSheet(
            episodeId = chipEpisodeId,
            onDismiss = { openEpisodeId = null },
            onJumpToTimeline = { startAt -> snackbarScope.launch { jumpToTimeline(startAt) } }
        )
    }
}

/**
 * 메시지 하나 + (해당하면) 그 위의 날짜 구분선.
 *
 * [WHY] 예전 buildChatRows(전체 리스트 전처리)는 Paging 과 함께 쓸 수 없다 — 리스트 전체가
 * 한 번에 손에 없다. 대신 **항목별 경계 판정**으로 바꿨다: 이 메시지가 더 과거인 이웃
 * [older]와 날짜가 다르면(= 그 날의 첫 메시지) 구분선을 위에 그린다. reverseLayout 에서
 * 이웃(index+1)은 화면상 위에 있으므로, 아이템 내부 Column 의 [구분선, 버블] 순서가 시각적으로
 * "구분선이 그 날의 첫 버블 위"가 된다. [older]가 placeholder(null)인 미로드 경계에서는
 * 구분선을 생략한다 — 잘못 그리는 것보다 안 그리는 쪽이 덜 이상하다.
 */
/**
 * 타임라인 한 행 — 회수 칩 라벨 요청 + [MessageWithDate].
 *
 * [WHY] 라이브 테일과 페이징 과거 두 items 블록이 같은 행 본문(칩 id 계산, 라벨 요청, 버블)을
 * 복제하고 있었다. 이웃([older]) 계산만 블록마다 다르므로 그것만 밖에서 받는다.
 */
@Composable
private fun TimelineRow(
    message: ChatMessage,
    older: ChatMessage?,
    today: java.time.LocalDate,
    episodeChipLabels: Map<String, String?>,
    highlightStartAt: Long?,
    onEnsureChipLabel: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onCopy: (String) -> Unit,
    speakingMessageId: String? = null,
    onToggleSpeak: (ChatMessage) -> Unit = {},
    resultStates: Map<String, com.kosmos.app.feature.chat.result.ResultCardState> = emptyMap(),
    onEnsureResult: (com.kosmos.app.domain.model.MessageResult) -> Unit = {},
    onResultClick: (ChatMessage) -> Unit = {},
    chips: List<com.kosmos.app.feature.chat.result.ChatChip> = emptyList(),
    onChipClick: (com.kosmos.app.feature.chat.result.ChatChip) -> Unit = {}
) {
    message.result?.let { result -> LaunchedEffect(result) { onEnsureResult(result) } }
    val chipId = message.recallEpisodeIds.firstOrNull()
    if (chipId != null) {
        LaunchedEffect(chipId) { onEnsureChipLabel(chipId) }
    }
    MessageWithDate(
        message = message,
        older = older,
        today = today,
        onCopy = { onCopy(message.content) },
        recallChipLabel = chipId?.let { episodeChipLabels[it] },
        onRecallChipClick = { if (chipId != null) onOpenEpisode(chipId) },
        highlighted = isJumpTarget(message, older, highlightStartAt),
        isSpeaking = message.id == speakingMessageId,
        onToggleSpeak = { onToggleSpeak(message) },
        result = message.result?.let { resultStates[com.kosmos.app.feature.chat.result.ChatExtrasViewModel.keyOf(it)] },
        onResultClick = {
            com.kosmos.app.feature.chat.result.ChatChipRules.recallEpisode(message.result)?.let(onOpenEpisode) ?: onResultClick(message)
        },
        chips = chips,
        onChipClick = onChipClick
    )
}

@Composable
private fun MessageWithDate(
    message: ChatMessage,
    older: ChatMessage?,
    today: java.time.LocalDate,
    onCopy: () -> Unit,
    recallChipLabel: String? = null,
    onRecallChipClick: () -> Unit = {},
    highlighted: Boolean = false,
    isSpeaking: Boolean = false,
    onToggleSpeak: () -> Unit = {},
    result: com.kosmos.app.feature.chat.result.ResultCardState? = null,
    onResultClick: () -> Unit = {},
    chips: List<com.kosmos.app.feature.chat.result.ChatChip> = emptyList(),
    onChipClick: (com.kosmos.app.feature.chat.result.ChatChip) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val label = dateLabelIfBoundary(message, older, today)
        if (label != null) DateSeparator(label)

        // 점프 도착 하이라이트 — 배경 은은한 강조 1회성 (jumpToTimeline 후 2.5초).
        val bubbleModifier = if (highlighted) {
            Modifier.background(
                KosmosTheme.colors.brand.copy(alpha = 0.10f),
                RoundedCornerShape(20.dp)
            )
        } else Modifier

        Box(modifier = bubbleModifier) {
            when {
                message.role == ChatMessage.Role.USER -> ChatBubbleUser(
                    text = message.content,
                    inputType = message.inputType,
                    onLongPress = onCopy
                )
                // 비서가 스스로 시작한 아침 브리핑 — 말풍선이 아니라 "도착한 카드" (A′-1).
                message.inputType == com.kosmos.app.domain.model.InputType.BRIEFING ->
                    BriefingCard(text = message.content, onLongPress = onCopy)
                else -> ChatBubbleAssistant(
                    text = message.content,
                    thinkingProcess = message.thinkingProcess,
                    searchUsed = message.searchUsed,
                    recallChipLabel = recallChipLabel,
                    onRecallChipClick = onRecallChipClick,
                    onLongPress = onCopy,
                    isSpeaking = isSpeaking,
                    onToggleSpeak = onToggleSpeak,
                    result = result,
                    onResultClick = onResultClick.takeIf { result != null },
                    chips = chips,
                    onChipClick = onChipClick,
                    // 방금(10초 안) 저장된 답만 — 대화를 다시 열거나 위로 올렸다 내려올 때는 움직이지 않는다.
                    animateResult = com.kosmos.app.ui.component.LocalClock.current.millis() - message.createdAt < NEW_RESULT_WINDOW_MS
                )
            }
        }
    }
}

private const val NEW_RESULT_WINDOW_MS = 10_000L

/**
 * 채팅 상단 요약 한 줄(0.38.2) — 다음 알림·오늘 일정·남은 할 일. 누르면 해당 화면(결정 2: 할 일 탭·일정 화면).
 *
 * [WHY] 타일 3칸이 대화 위를 크게 차지했다 — 아이콘이 든 캡슐 한 줄로 줄여 대화가 보이는 높이를 늘린다(M7b 시안, 사용자 선택).
 */
@Composable
private fun SummaryTiles(
    summary: com.kosmos.app.feature.chat.result.ChatSummary,
    onOpenTasks: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(44.dp)
            .kosmosSurface(shape = RoundedCornerShape(99.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummarySegment(com.kosmos.app.R.drawable.ic_ph_bell, "다음", summary.nextReminder ?: "없음", "다음 알림", onOpenTasks, Modifier.weight(1.3f))
        SummaryDivider()
        SummarySegment(com.kosmos.app.R.drawable.ic_ph_calendar_blank, "일정", "${summary.todaySchedules}", "오늘 일정", onOpenCalendar, Modifier.weight(1f))
        SummaryDivider()
        SummarySegment(com.kosmos.app.R.drawable.ic_ph_check_square, "할 일", "${summary.pendingTasks}", "남은 할 일", onOpenTasks, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryDivider() {
    Box(Modifier.width(1.dp).height(20.dp).background(KosmosTheme.colors.border))
}

@Composable
private fun SummarySegment(
    @androidx.annotation.DrawableRes icon: Int,
    label: String,
    value: String,
    accessibleLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "$accessibleLabel 보기", onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.kosmos.app.ui.component.KosmosIcon(icon, contentDescription = null, size = 18.dp, tint = KosmosTheme.colors.brandText)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = KosmosTheme.colors.textSecondary)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = KosmosTheme.colors.textPrimary, maxLines = 1)
    }
}

/**
 * [message]가 점프 도착 지점(에피소드 첫 메시지)인지 판정합니다 — startAt 이후이면서 더 과거
 * 이웃은 그 이전인 경계 메시지. id 를 모른 채 점프하므로 시각으로 자기를 식별하게 한다.
 */
private fun isJumpTarget(message: ChatMessage, older: ChatMessage?, highlightStartAt: Long?): Boolean {
    if (highlightStartAt == null) return false
    return message.createdAt >= highlightStartAt &&
        (older == null || older.createdAt < highlightStartAt)
}

/** 원문 이동 후 대상이 로드되길 기다리는 상한 — 넘으면 바닥 정렬로 둔다. */
private const val JUMP_LOAD_WAIT_MS = 1_000L

/**
 * [index] 항목의 윗변을 목록 화면 윗변에 맞춥니다 — 원문 대화가 처음부터 읽히게(0.36.2 사용자 요청).
 *
 * [WHY] reverseLayout 의 `scrollToItem` 은 항목을 시작 가장자리(**바닥**)에 놓는다. 아래에 화면 한 장만큼의 대화가 없으면(최근 기억)
 * 목록이 바닥에서 멈춘다 — 그때는 그 대화가 처음부터 최신까지 한 화면에 다 보인다(사용자 결정: 임시 여백을 두지 않는다).
 */
internal suspend fun alignItemTop(listState: androidx.compose.foundation.lazy.LazyListState, index: Int) {
    val info = listState.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val delta = topAlignScrollDelta(item.offset, item.size, info.viewportEndOffset - info.afterContentPadding)
    if (delta != 0) listState.scrollBy(delta.toFloat())
}

/**
 * reverseLayout 목록에서 항목 먼 쪽 끝(화면상 윗변)을 [visibleEnd] 에 맞추는 `scrollBy` 양. 오프셋은 모두 시작(바닥) 기준이다.
 * 음수 = 더 최신 쪽으로 스크롤(항목이 위로 올라감), 양수 = 항목이 화면보다 커서 윗변이 잘린 경우 아래로 내림.
 */
internal fun topAlignScrollDelta(itemOffset: Int, itemSize: Int, visibleEnd: Int): Int =
    (itemOffset + itemSize) - visibleEnd

/** 미로드 placeholder 자리 — 점프 직후 Paging 이 채우기 전까지의 스켈레톤. */
@Composable
private fun PlaceholderBubble() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .height(40.dp)
            .kosmosSurface(shape = RoundedCornerShape(16.dp))
    )
}

private fun copyToClipboard(
    scope: kotlinx.coroutines.CoroutineScope,
    clipboard: androidx.compose.ui.platform.Clipboard,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    content: String
) {
    // [WHY] LocalClipboard 의 setClipEntry 는 suspend 다. 스낵바용 스코프에서 복사와 안내를
    // 순서대로 처리한다 — onLongPress 의 `() -> Unit` 콜백 계약은 그대로 유지된다.
    scope.launch {
        clipboard.setClipEntry(
            androidx.compose.ui.platform.ClipEntry(
                android.content.ClipData.newPlainText("채팅 메시지", content)
            )
        )
        snackbarHostState.showSnackbar("메시지를 복사했어요.")
    }
}

/**
 * [message]가 [older]와 다른 날이면(그 날의 첫 메시지) 구분선 라벨을 돌려줍니다.
 * [WHY] [today] 는 인자다 — 예전에는 여기서 벽시계를 읽어 판정이 실행 시각에 묶였다(AGENTS §2-④).
 */
internal fun dateLabelIfBoundary(
    message: ChatMessage,
    older: ChatMessage?,
    today: java.time.LocalDate,
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
): String? {
    if (older == null) return null
    val date = java.time.Instant.ofEpochMilli(message.createdAt).atZone(zone).toLocalDate()
    val olderDate = java.time.Instant.ofEpochMilli(older.createdAt).atZone(zone).toLocalDate()
    if (date == olderDate) return null
    return com.kosmos.app.domain.util.IsoDateTimeParser.dayLabelKorean(date, today)
}
