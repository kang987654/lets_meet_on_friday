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
    onToggleWebSearch: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showStatusSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current

    // 날짜 구분선을 포함한 표시용 행 목록 (C-1)
    val chatRows = remember(uiState.messages) { buildChatRows(uiState.messages) }

    // [WHY] 사용자가 위로 스크롤해 과거 대화를 읽는 중이라면 새 토큰이 도착해도 끌어내리지 않는다.
    // 스크롤 제스처가 끝난 시점에 바닥 여부를 기록해 자동 스크롤 여부를 판단한다. (C-3)
    var userScrolledAway by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress ->
                if (!inProgress) userScrolledAway = listState.canScrollForward
            }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatRows.size, uiState.isInFlight) {
        if (chatRows.isNotEmpty() && !userScrolledAway) {
            listState.animateScrollToItem(chatRows.size) // scroll past last item for indicator
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
            // [WHY] 콜백은 메인 스레드다 — 최대 10MB 파일 전체 읽기는 IO 로 옮긴다. 전송 시점
            // 읽기(ChatViewModel)는 같은 이유로 이미 IO 였는데 인테이크 쪽만 메인에 남아 있었다.
            snackbarScope.launch {
                val shared: SharedInput? = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val mimeType = contentResolver.getType(uri)
                        if (mimeType?.startsWith("image/") == true) {
                            contentResolver.openInputStream(uri)?.use { inputStream ->
                                SharedInput.Image(
                                    uri = uri,
                                    sizeBytes = inputStream.readBytes().size.toLong()
                                )
                            }
                        } else {
                            contentResolver.openInputStream(uri)?.use { inputStream ->
                                val textContent = inputStream.bufferedReader().use { it.readText() }
                                var fileName = "document.txt"
                                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (cursor.moveToFirst() && nameIndex != -1) {
                                        fileName = cursor.getString(nameIndex)
                                    }
                                }
                                // [WHY] 캡은 Constants.MAX_ATTACHED_DOC_CHARS 로 예산에서 파생된다.
                                // 예전 2500 은 예산 6000 시절의 유물 — 그대로 두면 그 턴의 KV 가
                                // GPU 숫자 깨짐 발병점을 넘고, 다음 턴부터는 슬라이딩 윈도우에서
                                // 통째로 탈락해 모델이 문서를 본 적 없는 상태가 됐다.
                                if (textContent.length > com.kosmos.app.core.common.Constants.MAX_ATTACHED_DOC_CHARS) {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "문서가 길어 앞부분만 첨부돼요. 긴 문서 요약은 아직 지원하지 않아요."
                                        )
                                    }
                                }
                                SharedInput.Document(
                                    uri = uri,
                                    fileName = fileName,
                                    textContent = textContent.take(
                                        com.kosmos.app.core.common.Constants.MAX_ATTACHED_DOC_CHARS
                                    )
                                )
                            }
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // [WHY] key가 없으면 스트리밍 append마다 전체 행이 리바인드되고
                    // 아코디언 확장 상태가 엉뚱한 버블에 붙을 수 있다.
                    items(chatRows, key = { it.key }) { row ->
                        when (row) {
                            is ChatRow.DateHeader -> DateSeparator(row.label)
                            is ChatRow.Message -> {
                                val message = row.message
                                val copyMessage = {
                                    // [WHY] LocalClipboard 의 setClipEntry 는 suspend 다. 스낵바용 스코프가
                                    // 이미 있으므로 복사와 안내를 같은 코루틴에서 순서대로 처리한다 —
                                    // onLongPress 의 `() -> Unit` 콜백 계약은 그대로 유지된다.
                                    snackbarScope.launch {
                                        clipboard.setClipEntry(
                                            androidx.compose.ui.platform.ClipEntry(
                                                android.content.ClipData.newPlainText("채팅 메시지", message.content)
                                            )
                                        )
                                        snackbarHostState.showSnackbar("메시지를 복사했어요.")
                                    }
                                    Unit
                                }
                                if (message.role == ChatMessage.Role.USER) {
                                    ChatBubbleUser(
                                        text = message.content,
                                        inputType = message.inputType,
                                        onLongPress = copyMessage
                                    )
                                } else {
                                    ChatBubbleAssistant(
                                        text = message.content,
                                        thinkingProcess = message.thinkingProcess,
                                        searchUsed = message.searchUsed,
                                        onLongPress = copyMessage
                                    )
                                }
                            }
                        }
                    }
                    
                    if (uiState.streamingText != null || uiState.streamingThinking != null) {
                        item {
                            ChatBubbleAssistant(text = uiState.streamingText ?: "", thinkingProcess = uiState.streamingThinking)
                        }
                    }

                    if (uiState.isInFlight && uiState.streamingText == null) {
                        item {
                            TypingIndicator()
                        }
                    }
                }
            }

            // 최신으로 이동 FAB — 위로 스크롤한 상태에서만 노출 (C-3)
            if (listState.canScrollForward) {
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = {
                        userScrolledAway = false
                        snackbarScope.launch {
                            if (chatRows.isNotEmpty()) listState.animateScrollToItem(chatRows.size)
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
}

/** 채팅 목록의 행 — 메시지와 날짜 구분선을 한 리스트로 표현합니다. */
private sealed class ChatRow {
    abstract val key: String

    data class DateHeader(val label: String, override val key: String) : ChatRow()
    data class Message(val message: ChatMessage) : ChatRow() {
        override val key: String get() = message.id
    }
}

/**
 * 메시지 목록에 날짜가 바뀌는 지점마다 구분선 행을 삽입합니다.
 * [WHY] 대화가 여러 날 누적되면 어떤 메시지가 언제 것인지 알 수 없어 맥락 파악이 어렵다.
 */
private fun buildChatRows(messages: List<ChatMessage>): List<ChatRow> {
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    val rows = mutableListOf<ChatRow>()
    var lastDate: java.time.LocalDate? = null

    messages.forEach { message ->
        val date = java.time.Instant.ofEpochMilli(message.createdAt).atZone(zone).toLocalDate()
        if (date != lastDate) {
            val label = when (date) {
                today -> "오늘"
                today.minusDays(1) -> "어제"
                else -> date.format(
                    java.time.format.DateTimeFormatter.ofPattern("M월 d일", java.util.Locale.KOREAN)
                )
            }
            rows.add(ChatRow.DateHeader(label = label, key = "date-$date"))
            lastDate = date
        }
        rows.add(ChatRow.Message(message))
    }
    return rows
}
