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
import androidx.hilt.navigation.compose.hiltViewModel
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
/**
 * ChatScreen: 핵심 화면 (Main View)
 * 
 * [Architecture Context]
 * - Layer: UI (Presentation)
 * - Dependencies: ChatViewModel (Hilt), ModelRunner (Domain)
 * 
 * [Key Flow]
 * 1. 사용자가 하단 ChatInputBar를 통해 텍스트/음성 메시지를 입력
 * 2. ViewModel로 전달되어 Local LLM(Gemma)에 의해 추론 수행
 * 3. 응답이 스트리밍되어 LazyColumn에 실시간 업데이트됨
 * 4. 상단 헤더(CustomChatHeader)를 통해 AI 상태(Loading, Ready 등) 표시
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit = {},
    webSearchEnabled: Boolean = false,
    onToggleWebSearch: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

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
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val mimeType = contentResolver.getType(uri)
                if (mimeType?.startsWith("image/") == true) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        val sharedImage = SharedInput.Image(
                            uri = uri,
                            sizeBytes = bytes.size.toLong()
                        )
                        viewModel.setSharedInput(sharedImage)
                    }
                } else {
                    // Try to read as text document
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val textContent = inputStream.bufferedReader().use { it.readText() }
                        // Get file name
                        var fileName = "document.txt"
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && nameIndex != -1) {
                                fileName = cursor.getString(nameIndex)
                            }
                        }
                        val sharedDoc = SharedInput.Document(
                            uri = uri,
                            fileName = fileName,
                            textContent = textContent.take(2500) // Limit text to prevent exceeding token bounds
                        )
                        viewModel.setSharedInput(sharedDoc)
                    }
                }
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleRecording()
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
    // 오류와 토글 피드백을 스낵바 한 곳으로 모으고, 문구는 ErrorMessages로 인간화한다.
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val snackbarScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(com.kosmos.app.core.mapper.ErrorMessages.userMessage(error))
        viewModel.dismissError()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            com.kosmos.app.feature.chat.CustomChatHeader(
                onSettingsClick = onSettingsClick,
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
                onStopGeneration = { viewModel.cancelGeneration() }
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
                val warningMessage = uiState.warningMessage
                if (warningMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 발열 경고 배너 — 테마 warning 토큰 기반(라이트/다크 모두 대비 확보)
                            .background(KosmosTheme.colors.warning.copy(alpha = 0.15f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = warningMessage,
                            color = KosmosTheme.colors.warning,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                }

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
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(message.content))
                                    snackbarScope.launch { snackbarHostState.showSnackbar("메시지를 복사했어요.") }
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
                } // end LazyColumn
            } // end Column

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
        } // end Box
    } // end Scaffold

    // 캘린더 초안이 없는 일반 승인(메모리 저장 등)만 승인 시트로 표시 — 초안 카드와 이중 노출 방지
    val request = uiState.pendingApproval
    if (request != null && request.calendarDraft == null) {
        com.kosmos.app.feature.approval.ApprovalSheet(
            request = request,
            onApprove = { viewModel.approvePendingRequest() },
            onReject = { viewModel.rejectPendingRequest() }
        )
    }

}

// [WHY] takeLast(5) 등 문자열 슬라이싱은 초/오프셋(Z, +09:00)이 붙은 ISO에서 깨지므로
// java.time 파서로 기기 시간대 기준 표시 값을 계산한다. 파싱 실패 시 원문 폴백.
private fun parseDraftDateTime(iso: String): java.time.LocalDateTime? {
    val zoneId = java.time.ZoneId.systemDefault()
    return runCatching { java.time.OffsetDateTime.parse(iso).atZoneSameInstant(zoneId).toLocalDateTime() }.getOrNull()
        ?: runCatching { java.time.Instant.parse(iso).atZone(zoneId).toLocalDateTime() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(iso) }.getOrNull()
}

private fun formatDraftDate(iso: String): String =
    parseDraftDateTime(iso)?.toLocalDate()?.toString() ?: iso

private fun formatDraftTime(iso: String): String =
    parseDraftDateTime(iso)?.toLocalTime()
        ?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        ?: iso

@Composable
fun CustomChatHeader(
    onSettingsClick: () -> Unit = {},
    webSearchEnabled: Boolean = false,
    onToggleWebSearch: (Boolean) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "KOSMOS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 1.sp,
            color = KosmosTheme.colors.textPrimary
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            // 웹 검색 허용 토글 — ON: 승인 없이 검색 허용 / OFF(기본): 모델에 툴 미노출 + 실행 차단
            IconButton(
                onClick = { onToggleWebSearch(!webSearchEnabled) },
                modifier = Modifier
                    .size(36.dp)
                    .glassEffect(shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "WebSearchToggle",
                    tint = if (webSearchEnabled) KosmosTheme.colors.accent else KosmosTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(36.dp)
                    .glassEffect(shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = KosmosTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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

@Composable
private fun DateSeparator(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(KosmosTheme.colors.border)
        )
        Text(
            text = label,
            color = KosmosTheme.colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(KosmosTheme.colors.border)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatBubbleUser(
    text: String,
    inputType: com.kosmos.app.domain.model.InputType = com.kosmos.app.domain.model.InputType.TEXT,
    onLongPress: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(KosmosTheme.colors.accent.copy(alpha = 0.25f), KosmosTheme.colors.accentAlt.copy(alpha = 0.2f))
                    ),
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
                )
                .border(
                    width = 1.dp,
                    color = KosmosTheme.colors.accent.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                if (inputType == com.kosmos.app.domain.model.InputType.IMAGE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🖼️", modifier = Modifier.padding(end = 4.dp))
                        Text(
                            text = "첨부된 이미지",
                            color = KosmosTheme.colors.textMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                } else if (inputType == com.kosmos.app.domain.model.InputType.VOICE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎤", modifier = Modifier.padding(end = 4.dp))
                        Text(
                            text = "음성 메시지",
                            color = KosmosTheme.colors.textMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                }
                Text(
                    text = text,
                    color = KosmosTheme.colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatBubbleAssistant(
    text: String,
    thinkingProcess: String? = null,
    searchUsed: Boolean = false,
    onLongPress: () -> Unit = {}
) {
    var isThinkingExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (thinkingProcess != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(
                            backgroundColor = KosmosTheme.colors.glassMid,
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                        )
                        .clickable { isThinkingExpanded = !isThinkingExpanded }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤔", modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = if (isThinkingExpanded) "고민 과정 숨기기" else "고민 과정 보기",
                            color = KosmosTheme.colors.textSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                
                if (isThinkingExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassEffect(
                                backgroundColor = KosmosTheme.colors.glass,
                                shape = RoundedCornerShape(0.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = thinkingProcess,
                            color = KosmosTheme.colors.textMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            val shape = if (thinkingProcess != null) {
                RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
            } else {
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(shape = shape)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides KosmosTheme.colors.textPrimary
                ) {
                    ProvideTextStyle(
                        value = MaterialTheme.typography.bodyMedium.copy(color = KosmosTheme.colors.textPrimary)
                    ) {
                        RichText {
                            Markdown(content = text)
                        }
                    }
                }
            }

            // [WHY] 이 답변이 네트워크로 나갔는지를 사용자가 볼 수 있어야 한다. 프라이버시
            // 우선 원칙에서 웹 검색은 토글로 명시 허용하는 동작이므로, 실제로 쓰인 턴을
            // 표시하지 않으면 토글의 의미가 절반만 남는다.
            if (searchUsed) {
                Text(
                    text = "🌐 위키백과 검색 결과를 참고했어요",
                    color = KosmosTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 14.dp, top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .glassEffect(shape = RoundedCornerShape(18.dp))
        ) {
            com.kosmos.app.ui.component.ThinkingDots()
        }
    }
}

/**
 * 첨부 이미지 썸네일 (C-4).
 * [WHY] 외부 이미지 로딩 라이브러리를 추가하지 않고, `inSampleSize` 다운샘플 디코딩을
 * IO 디스패처에서 수행해 메인 스레드 부담과 메모리 사용을 함께 억제한다.
 */
@Composable
private fun AttachmentThumbnail(uri: Uri, sizeDp: Int = 48) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val targetPx = remember(sizeDp) { with(density) { sizeDp.dp.roundToPx() } }

    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, uri, targetPx) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) {
                    sample *= 2
                }
                val options = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }
                context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, options)
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(KosmosTheme.colors.glassMid)
    ) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "AttachmentThumbnail",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private enum class InputAction { SEND, MIC, STOP }

@Composable
fun ChatInputBar(
    isLoading: Boolean,
    isRecording: Boolean,
    sharedInput: com.kosmos.app.platform.share.SharedInput?,
    onClearSharedInput: () -> Unit,
    onSend: (String) -> Unit,
    onMicClick: () -> Unit,
    onAttachClick: () -> Unit,
    onStopGeneration: () -> Unit = {}
) {
    var textState by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(sharedInput) {
        if (sharedInput is com.kosmos.app.platform.share.SharedInput.Text) {
            textState = TextFieldValue(sharedInput.content)
            onClearSharedInput() // Consume the shared input text
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        if (sharedInput is com.kosmos.app.platform.share.SharedInput.Image) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .glassEffect(shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AttachmentThumbnail(uri = sharedInput.uri)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.image_attached), color = KosmosTheme.colors.accent, style = MaterialTheme.typography.bodyMedium)
                            Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.image_size_kb, sharedInput.sizeBytes / 1024), color = KosmosTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    IconButton(onClick = { onClearSharedInput() }) {
                        Text("X", color = KosmosTheme.colors.textMuted)
                    }
                }
            }
        } else if (sharedInput is com.kosmos.app.platform.share.SharedInput.Document) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .glassEffect(shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Document Attached", color = KosmosTheme.colors.accent, style = MaterialTheme.typography.bodyMedium)
                        Text(sharedInput.fileName, color = KosmosTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onClearSharedInput() }) {
                        Text("X", color = KosmosTheme.colors.textMuted)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .glassEffect(
                    shape = RoundedCornerShape(28.dp),
                    borderColor = KosmosTheme.colors.borderHigh
                )
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onAttachClick() },
                enabled = !isLoading,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = com.kosmos.app.R.drawable.ic_attach),
                    contentDescription = "Attach",
                    tint = if (!isLoading) KosmosTheme.colors.textSecondary else KosmosTheme.colors.textMuted,
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                if (textState.text.isEmpty()) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.chat_input_hint),
                        color = KosmosTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                BasicTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = KosmosTheme.colors.textPrimary),
                    cursorBrush = SolidColor(KosmosTheme.colors.accent),
                    enabled = !isLoading,
                    maxLines = 5
                )
            }
            
            // [WHY] 생성 중에는 전송/마이크 대신 정지 버튼을 노출한다 (E2E의 "Send"/"Stop"
            // 셀렉터와 겹치지 않도록 정지 버튼은 "StopGeneration"을 쓴다).
            val inputAction = when {
                isLoading -> InputAction.STOP
                textState.text.isNotBlank() -> InputAction.SEND
                else -> InputAction.MIC
            }

            androidx.compose.animation.Crossfade(
                targetState = inputAction,
                modifier = Modifier.size(40.dp),
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                label = "send_mic_transition"
            ) { action ->
                if (action == InputAction.STOP) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(KosmosTheme.colors.glass)
                            .border(1.dp, KosmosTheme.colors.accent, shape = androidx.compose.foundation.shape.CircleShape)
                            .clickable { onStopGeneration() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = com.kosmos.app.R.drawable.ic_stop),
                            contentDescription = "StopGeneration",
                            tint = KosmosTheme.colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (action == InputAction.SEND) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(KosmosTheme.colors.accent, KosmosTheme.colors.accentAlt)
                                )
                            )
                            .clickable(enabled = !isLoading) {
                                onSend(textState.text)
                                textState = TextFieldValue("")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = com.kosmos.app.R.drawable.ic_send),
                            contentDescription = "Send",
                            tint = KosmosTheme.colors.onAccent,
                            modifier = Modifier.size(20.dp).padding(start = 2.dp)
                        )
                    }
                } else {
                    val micBg = if (isRecording) KosmosTheme.colors.danger.copy(alpha = 0.2f) else KosmosTheme.colors.glass
                    val micBorder = if (isRecording) KosmosTheme.colors.danger else KosmosTheme.colors.border
                    val micIconColor = if (isRecording) KosmosTheme.colors.danger else KosmosTheme.colors.textSecondary
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(micBg)
                            .border(1.dp, micBorder, shape = androidx.compose.foundation.shape.CircleShape)
                            .clickable(enabled = !isLoading) { onMicClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = if (isRecording) com.kosmos.app.R.drawable.ic_stop else com.kosmos.app.R.drawable.ic_mic),
                            contentDescription = if (isRecording) "Stop" else "Microphone",
                            tint = micIconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDraftCard(
    draft: com.kosmos.app.domain.model.CalendarDraft,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(
                backgroundColor = KosmosTheme.colors.surface,
                shape = RoundedCornerShape(24.dp),
                borderColor = KosmosTheme.colors.borderHigh
            )
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(KosmosTheme.colors.accent, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("K", color = KosmosTheme.colors.onAccent, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Kosmos suggests an event",
                color = KosmosTheme.colors.textMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(
                    backgroundColor = KosmosTheme.colors.glass,
                    shape = RoundedCornerShape(16.dp)
                )
                .border(1.dp, KosmosTheme.colors.border, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = draft.title,
                    color = KosmosTheme.colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📅", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDraftDate(draft.startIso),
                        color = KosmosTheme.colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🕒", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buildString {
                            append(formatDraftTime(draft.startIso))
                            draft.endIso?.let { append(" - ").append(formatDraftTime(it)) }
                        },
                        color = KosmosTheme.colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (draft.note != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text("💬", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = draft.note ?: "",
                            color = KosmosTheme.colors.textMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onReject() }
                    .background(KosmosTheme.colors.danger.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .border(1.dp, KosmosTheme.colors.border, RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Reject", color = KosmosTheme.colors.danger, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onApprove() }
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(KosmosTheme.colors.accent.copy(alpha = 0.2f), KosmosTheme.colors.accentAlt.copy(alpha = 0.2f))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, KosmosTheme.colors.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Approve & Save", color = KosmosTheme.colors.accent, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        }
    }
}
