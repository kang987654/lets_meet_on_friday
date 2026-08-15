package com.kosmos.app.feature.chat

import com.kosmos.app.ui.theme.KosmosTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.kosmos.app.domain.model.ChatMessage
import androidx.compose.ui.unit.sp
import com.kosmos.app.ui.component.glassEffect
import kotlinx.coroutines.launch

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kosmos.app.platform.share.SharedInput
import com.kosmos.app.domain.modelrunner.ModelLoadState
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
// [WHY] ChatScreen.kt(1,200여 줄, 12개 컴포저블 동거)를 응집 단위로 분리했다
// (2026-08-15, MVP 감사 refactor-ui) — 순수 이동이며 동작 변경이 없다. 이 파일: 화면 조립(ChatScreen)과 목록 행 모델(ChatRow). 파일 헤더 KDoc 2벌 중 낡은 쪽은 삭제했다.

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
    onJumpConsumed: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
        userScrolledAway = true
        highlightStartAt = startAt
        listState.scrollToItem(target.coerceIn(0, maxOf(0, lastIndex)))
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
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val snackbarScope = androidx.compose.runtime.rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // [WHY] 콜백은 메인 스레드지만 여기서는 **큰 읽기를 아예 하지 않는다** — 이미지는
            // SIZE 메타데이터 쿼리(빠름), 문서는 캡(MAX_ATTACHED_DOC_CHARS)만큼만 경계 읽기.
            // 예전에는 10MB 전체를 읽어 ANR 위험이 있었고, 그것을 IO 코루틴으로 옮기자
            // 프리뷰 갱신이 비동기가 되어 E2E 와 경합했다(waitForIdle 이 IO 를 기다리지 않는다).
            // 읽는 양을 줄이는 것이 스레드를 옮기는 것보다 나은 해법이다. 전체 바이트는
            // 전송 시점에 ChatViewModel 이 IO 에서 읽는다.
            val shared: SharedInput? = try {
                val mimeType = contentResolver.getType(uri)
                if (mimeType?.startsWith("image/") == true) {
                    val sizeBytes = contentResolver.query(
                        uri, arrayOf(OpenableColumns.SIZE), null, null, null
                    )?.use { cursor ->
                        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst() && idx != -1 && !cursor.isNull(idx)) cursor.getLong(idx) else null
                    } ?: contentResolver.openInputStream(uri)?.use { it.available().toLong() }
                    if (sizeBytes != null) SharedInput.Image(uri = uri, sizeBytes = sizeBytes) else null
                } else {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        // [WHY] 캡은 Constants.MAX_ATTACHED_DOC_CHARS 로 예산에서 파생된다.
                        // 예전 2500 은 예산 6000 시절의 유물 — 그대로 두면 그 턴의 KV 가
                        // GPU 숫자 깨짐 발병점을 넘고, 다음 턴부터는 슬라이딩 윈도우에서
                        // 통째로 탈락해 모델이 문서를 본 적 없는 상태가 됐다. +1 은 절단
                        // 여부 감지용이다.
                        val cap = com.kosmos.app.core.common.Constants.MAX_ATTACHED_DOC_CHARS
                        val buffer = CharArray(cap + 1)
                        val read = inputStream.bufferedReader().read(buffer, 0, buffer.size)
                        val textContent = if (read <= 0) "" else String(buffer, 0, read)
                        var fileName = "document.txt"
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && nameIndex != -1) {
                                fileName = cursor.getString(nameIndex)
                            }
                        }
                        if (textContent.length > cap) {
                            snackbarScope.launch {
                                snackbarHostState.showSnackbar(
                                    "문서가 길어 앞부분만 첨부돼요. 긴 문서 요약은 아직 지원하지 않아요."
                                )
                            }
                        }
                        SharedInput.Document(
                            uri = uri,
                            fileName = fileName,
                            textContent = textContent.take(cap)
                        )
                    }
                }
            } catch (e: Exception) {
                null
            }
            if (shared != null) {
                viewModel.setSharedInput(shared)
            } else {
                // [WHY] 예전에는 catch 가 비어 있어(`// handle error`) 첨부 읽기가 실패하면
                // 아무 안내 없이 첨부가 사라졌다 — 사용자는 버튼이 고장 났다고 본다.
                // openInputStream 이 null 을 돌려주는 무예외 실패도 같은 안내로 덮는다.
                snackbarScope.launch {
                    snackbarHostState.showSnackbar("첨부 파일을 읽지 못했어요. 다른 파일을 선택해주세요.")
                }
            }
        }
    }

    // [WHY] 예전에는 거부 분기가 아예 없어 마이크를 거부하면 **아무 일도 일어나지 않았다** —
    // 사용자는 버튼이 고장 난 것으로 본다. PRD EC2 는 "음성 입력 비활성 + 텍스트 입력 안내"를
    // 요구한다. 문구는 `ErrorMessages` 의 것을 그대로 쓴다(화면마다 다른 말을 하지 않도록).
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleRecording()
        } else {
            snackbarScope.launch {
                snackbarHostState.showSnackbar(
                    com.kosmos.app.core.mapper.ErrorMessages.userMessage(
                        com.kosmos.app.core.common.AppError.PermissionDenied(
                            com.kosmos.app.core.security.PermissionPolicy.MICROPHONE
                        )
                    )
                )
            }
        }
    }

    // [WHY] 일괄 선요청(P4-11 제거) 대신 일정 승인 시점에 컨텍스트와 함께 캘린더 권한을 요청한다.
    // 거부해도 일정은 로컬 DB에 저장되고 기기 캘린더 동기화만 생략된다 (ADR-004 Graceful Degradation).
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 결과와 무관하게 진행 — 미승인 시 로컬 저장만 수행 */ }

    LaunchedEffect(uiState.pendingApproval) {
        val needsCalendarPermission = uiState.pendingApproval?.calendarDraft != null
        if (needsCalendarPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED
        ) {
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    // [WHY] 기존에는 uiState.error가 화면에 전혀 표시되지 않아 실패가 무음으로 사라졌다.
    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(com.kosmos.app.core.mapper.ErrorMessages.userMessage(error))
        viewModel.dismissError()
    }

    LaunchedEffect(uiState.searchFailedNotice) {
        if (!uiState.searchFailedNotice) return@LaunchedEffect
        snackbarHostState.showSnackbar("웹 검색 보강에 실패해 기기 안의 정보만으로 답했어요.")
        viewModel.dismissSearchFailedNotice()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            com.kosmos.app.feature.chat.CustomChatHeader(
                onMenuClick = onMenuClick,
                engineState = uiState.engineState,
                deviceStatus = uiState.deviceStatus,
                warningMessage = uiState.warningMessage,
                onStatusClick = { showStatusSheet = true }
            )
        },
        bottomBar = {
            ChatInputBar(
                isLoading = uiState.isInFlight,
                isRecording = uiState.isRecording,
                sharedInput = uiState.sharedInput,
                onClearSharedInput = { viewModel.clearSharedInput() },
                onSend = { text ->
                    if (text.isNotBlank()) {
                        viewModel.sendMessage(text)
                    }
                },
                onMicClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.toggleRecording()
                    } else {
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onAttachClick = { imagePickerLauncher.launch("*/*") },
                onStopGeneration = { viewModel.cancelGeneration() },
                webSearchEnabled = webSearchEnabled,
                onToggleWebSearch = { enabled ->
                    onToggleWebSearch(enabled)
                    snackbarScope.launch {
                        snackbarHostState.showSnackbar(
                            if (enabled) "웹 검색 허용됨 — 위키피디아 검색을 사용할 수 있어요."
                            else "웹 검색 차단됨 — 기기 안에서만 답변해요."
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // [WHY] reverseLayout — 선언 순서대로 index 0(바닥)부터 위로 쌓인다:
                // 스트리밍/타이핑 → 라이브 테일(최신순) → 페이징된 과거(최신순 DESC 그대로).
                // 과거로 스크롤하면 Paging 이 다음 페이지를 로드한다.
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                        val chipId = message.recallEpisodeIds.firstOrNull()
                        if (chipId != null) {
                            LaunchedEffect(chipId) { viewModel.ensureEpisodeChipLabel(chipId) }
                        }
                        MessageWithDate(
                            message = message,
                            older = older,
                            onCopy = { copyToClipboard(snackbarScope, clipboard, snackbarHostState, message.content) },
                            recallChipLabel = chipId?.let { episodeChipLabels[it] },
                            onRecallChipClick = { if (chipId != null) openEpisodeId = chipId },
                            highlighted = isJumpTarget(message, older, highlightStartAt)
                        )
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
                            val chipId = message.recallEpisodeIds.firstOrNull()
                            if (chipId != null) {
                                LaunchedEffect(chipId) { viewModel.ensureEpisodeChipLabel(chipId) }
                            }
                            MessageWithDate(
                                message = message,
                                older = older,
                                onCopy = { copyToClipboard(snackbarScope, clipboard, snackbarHostState, message.content) },
                                recallChipLabel = chipId?.let { episodeChipLabels[it] },
                                onRecallChipClick = { if (chipId != null) openEpisodeId = chipId },
                                highlighted = isJumpTarget(message, older, highlightStartAt)
                            )
                        }
                    }
                }
            }

            // 최신으로 이동 FAB — 위로 스크롤한 상태에서만 노출 (C-3).
            // reverseLayout 에서 "위로 스크롤함" = canScrollBackward, 최신 = index 0.
            if (listState.canScrollBackward) {
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = {
                        userScrolledAway = false
                        snackbarScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 20.dp),
                    containerColor = KosmosTheme.colors.glassHigh,
                    contentColor = KosmosTheme.colors.accent
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                        contentDescription = "ScrollToLatest"
                    )
                }
            }

            // [WHY] 캘린더 쓰기 승인(툴 콜)은 일반 다이얼로그 대신 구조화된 플로팅 초안 카드로
            // 표시한다 (절충안, 2026-07-31). 승인/거절은 동일한 ApprovalCoordinator 경로를 사용한다.
            val pendingDraft = uiState.pendingApproval?.calendarDraft
            if (pendingDraft != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    CalendarDraftCard(
                        draft = pendingDraft,
                        onApprove = { viewModel.approvePendingRequest() },
                        onReject = { viewModel.rejectPendingRequest() }
                    )
                }
            }
        }
    }

    // 캘린더 초안이 없는 일반 승인(메모리 저장 등)만 승인 시트로 표시 — 초안 카드와 이중 노출 방지
    val request = uiState.pendingApproval
    if (request != null && request.calendarDraft == null) {
        com.kosmos.app.feature.approval.ApprovalSheet(
            request = request,
            onApprove = { viewModel.approvePendingRequest() },
            onReject = { viewModel.rejectPendingRequest() }
        )
    }

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
@Composable
private fun MessageWithDate(
    message: ChatMessage,
    older: ChatMessage?,
    onCopy: () -> Unit,
    recallChipLabel: String? = null,
    onRecallChipClick: () -> Unit = {},
    highlighted: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val label = dateLabelIfBoundary(message, older)
        if (label != null) DateSeparator(label)

        // 점프 도착 하이라이트 — 배경 은은한 강조 1회성 (jumpToTimeline 후 2.5초).
        val bubbleModifier = if (highlighted) {
            Modifier.background(
                KosmosTheme.colors.accent.copy(alpha = 0.10f),
                RoundedCornerShape(20.dp)
            )
        } else Modifier

        Box(modifier = bubbleModifier) {
            if (message.role == ChatMessage.Role.USER) {
                ChatBubbleUser(
                    text = message.content,
                    inputType = message.inputType,
                    onLongPress = onCopy
                )
            } else {
                ChatBubbleAssistant(
                    text = message.content,
                    thinkingProcess = message.thinkingProcess,
                    searchUsed = message.searchUsed,
                    recallChipLabel = recallChipLabel,
                    onRecallChipClick = onRecallChipClick,
                    onLongPress = onCopy
                )
            }
        }
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

/** 미로드 placeholder 자리 — 점프 직후 Paging 이 채우기 전까지의 스켈레톤. */
@Composable
private fun PlaceholderBubble() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .height(40.dp)
            .glassEffect(shape = RoundedCornerShape(16.dp))
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

/** [message]가 [older]와 다른 날이면(그 날의 첫 메시지) 구분선 라벨을 돌려줍니다. */
private fun dateLabelIfBoundary(message: ChatMessage, older: ChatMessage?): String? {
    if (older == null) return null
    val zone = java.time.ZoneId.systemDefault()
    val date = java.time.Instant.ofEpochMilli(message.createdAt).atZone(zone).toLocalDate()
    val olderDate = java.time.Instant.ofEpochMilli(older.createdAt).atZone(zone).toLocalDate()
    if (date == olderDate) return null

    val today = java.time.LocalDate.now(zone)
    return when (date) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> date.format(
            java.time.format.DateTimeFormatter.ofPattern("M월 d일", java.util.Locale.KOREAN)
        )
    }
}
