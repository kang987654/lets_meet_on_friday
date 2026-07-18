package com.kosmos.app.feature.chat

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
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.feature.voice.VoiceOverlay
import androidx.compose.ui.unit.sp
import com.kosmos.app.ui.component.glassEffect

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
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showVoiceOverlay by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size, uiState.isInFlight) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size) // scroll past last item for indicator
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

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            com.kosmos.app.feature.chat.CustomChatHeader(onSettingsClick = onSettingsClick)
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
                onAttachClick = { imagePickerLauncher.launch("*/*") }
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
                if (uiState.warningMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(androidx.compose.ui.graphics.Color(0xFFFFF3E0)) // Soft Orange
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = uiState.warningMessage!!,
                            color = androidx.compose.ui.graphics.Color(0xFFE65100), // Dark Orange
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
                    items(uiState.messages) { message ->
                        if (message.role == ChatMessage.Role.USER) {
                            ChatBubbleUser(text = message.content, inputType = message.inputType)
                        } else {
                            ChatBubbleAssistant(text = message.content, thinkingProcess = message.thinkingProcess)
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

            if (uiState.pendingCalendarDraft != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    CalendarDraftCard(
                        draft = uiState.pendingCalendarDraft!!,
                        onApprove = { viewModel.approveCalendarDraft() },
                        onReject = { viewModel.rejectCalendarDraft() }
                    )
                }
            }
        } // end Box
    } // end Scaffold

    if (uiState.pendingApproval != null) {
        val request = uiState.pendingApproval!!
        com.kosmos.app.feature.approval.ApprovalSheet(
            request = request,
            onApprove = { viewModel.approvePendingRequest() },
            onReject = { viewModel.rejectPendingRequest() }
        )
    }

    // VoiceOverlay placeholder removed
}

@Composable
fun PulseGreenDot() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(com.kosmos.app.ui.theme.Success.copy(alpha = alpha), shape = RoundedCornerShape(3.dp))
    )
}

@Composable
fun CustomChatHeader(onSettingsClick: () -> Unit = {}) {
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
            color = com.kosmos.app.ui.theme.TextPrimary
        )
        
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(36.dp)
                .glassEffect(shape = androidx.compose.foundation.shape.CircleShape)
        ) {
            Text("⚙️", fontSize = 16.sp)
        }
    }
}

@Composable
fun ChatBubbleUser(text: String, inputType: com.kosmos.app.domain.model.InputType = com.kosmos.app.domain.model.InputType.TEXT) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(com.kosmos.app.ui.theme.Cyan.copy(alpha = 0.25f), com.kosmos.app.ui.theme.Violet.copy(alpha = 0.2f))
                    ),
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
                )
                .border(
                    width = 1.dp,
                    color = com.kosmos.app.ui.theme.Cyan.copy(alpha = 0.25f),
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
                            color = com.kosmos.app.ui.theme.TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                } else if (inputType == com.kosmos.app.domain.model.InputType.VOICE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎤", modifier = Modifier.padding(end = 4.dp))
                        Text(
                            text = "음성 메시지",
                            color = com.kosmos.app.ui.theme.TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                }
                Text(
                    text = text,
                    color = com.kosmos.app.ui.theme.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ChatBubbleAssistant(text: String, thinkingProcess: String? = null) {
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
                            backgroundColor = com.kosmos.app.ui.theme.GlassMidColor,
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                        )
                        .clickable { isThinkingExpanded = !isThinkingExpanded }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤔", modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = if (isThinkingExpanded) "고민 과정 숨기기" else "고민 과정 보기",
                            color = com.kosmos.app.ui.theme.TextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                
                if (isThinkingExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassEffect(
                                backgroundColor = com.kosmos.app.ui.theme.GlassColor,
                                shape = RoundedCornerShape(0.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = thinkingProcess,
                            color = com.kosmos.app.ui.theme.TextMuted,
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
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides com.kosmos.app.ui.theme.TextPrimary
                ) {
                    ProvideTextStyle(
                        value = MaterialTheme.typography.bodyMedium.copy(color = com.kosmos.app.ui.theme.TextPrimary)
                    ) {
                        RichText {
                            Markdown(content = text)
                        }
                    }
                }
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

@Composable
fun ChatInputBar(
    isLoading: Boolean,
    isRecording: Boolean,
    sharedInput: com.kosmos.app.platform.share.SharedInput?,
    onClearSharedInput: () -> Unit,
    onSend: (String) -> Unit,
    onMicClick: () -> Unit,
    onAttachClick: () -> Unit
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
                    Column {
                        Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.image_attached), color = com.kosmos.app.ui.theme.Cyan, style = MaterialTheme.typography.bodyMedium)
                        Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.image_size_kb, sharedInput.sizeBytes / 1024), color = com.kosmos.app.ui.theme.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onClearSharedInput() }) {
                        Text("X", color = com.kosmos.app.ui.theme.TextMuted)
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
                        Text("Document Attached", color = com.kosmos.app.ui.theme.Cyan, style = MaterialTheme.typography.bodyMedium)
                        Text(sharedInput.fileName, color = com.kosmos.app.ui.theme.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onClearSharedInput() }) {
                        Text("X", color = com.kosmos.app.ui.theme.TextMuted)
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
                    borderColor = com.kosmos.app.ui.theme.BorderHighColor
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
                    tint = if (!isLoading) com.kosmos.app.ui.theme.TextSecondary else com.kosmos.app.ui.theme.TextMuted,
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
                        color = com.kosmos.app.ui.theme.TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                BasicTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = com.kosmos.app.ui.theme.TextPrimary),
                    cursorBrush = SolidColor(com.kosmos.app.ui.theme.Cyan),
                    enabled = !isLoading,
                    maxLines = 5
                )
            }
            
            if (textState.text.isNotBlank()) {
                IconButton(
                    onClick = {
                        onSend(textState.text)
                        textState = TextFieldValue("")
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(com.kosmos.app.ui.theme.Cyan, com.kosmos.app.ui.theme.Violet)
                            ),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                ) {
                    Text(
                        text = "↑", 
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                val micBg = if (isRecording) com.kosmos.app.ui.theme.Danger.copy(alpha = 0.2f) else com.kosmos.app.ui.theme.GlassColor
                val micBorder = if (isRecording) com.kosmos.app.ui.theme.Danger else com.kosmos.app.ui.theme.BorderColor
                val micIconColor = if (isRecording) com.kosmos.app.ui.theme.Danger else com.kosmos.app.ui.theme.TextSecondary
                IconButton(
                    onClick = { onMicClick() },
                    enabled = !isLoading,
                    modifier = Modifier
                        .size(40.dp)
                        .background(micBg, shape = androidx.compose.foundation.shape.CircleShape)
                        .border(1.dp, micBorder, shape = androidx.compose.foundation.shape.CircleShape)
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
                backgroundColor = com.kosmos.app.ui.theme.SurfaceColor,
                shape = RoundedCornerShape(24.dp),
                borderColor = com.kosmos.app.ui.theme.BorderHighColor
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
                    .background(com.kosmos.app.ui.theme.Cyan, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("K", color = com.kosmos.app.ui.theme.BgColor, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Kosmos suggests an event",
                color = com.kosmos.app.ui.theme.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(
                    backgroundColor = com.kosmos.app.ui.theme.GlassColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .border(1.dp, com.kosmos.app.ui.theme.BorderColor, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = draft.title,
                    color = com.kosmos.app.ui.theme.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📅", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = draft.startIso.take(10),
                        color = com.kosmos.app.ui.theme.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🕒", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${draft.startIso.takeLast(5)} - ${draft.endIso?.takeLast(5) ?: ""}",
                        color = com.kosmos.app.ui.theme.TextSecondary,
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
                            color = com.kosmos.app.ui.theme.TextMuted,
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
                    .background(com.kosmos.app.ui.theme.Danger.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .border(1.dp, com.kosmos.app.ui.theme.BorderColor, RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Reject", color = com.kosmos.app.ui.theme.Danger, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onApprove() }
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(com.kosmos.app.ui.theme.Cyan.copy(alpha = 0.2f), com.kosmos.app.ui.theme.Violet.copy(alpha = 0.2f))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, com.kosmos.app.ui.theme.Cyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Approve & Save", color = com.kosmos.app.ui.theme.Cyan, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        }
    }
}
