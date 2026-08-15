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
// (2026-08-15, MVP 감사 refactor-ui) — 순수 이동이며 동작 변경이 없다. 이 파일: 입력바(텍스트·마이크·첨부 프리뷰).

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
    onStopGeneration: () -> Unit = {},
    // [WHY] 웹 검색 토글이 헤더에서 여기로 왔다 (시안 A′, M2-1) — 질문을 쓰는 맥락 옆이
    // 자연스럽고, 헤더의 토글·설정 겹침 문제가 원천 소멸한다. 기본값은 E2E 계약(ChatScreen
    // 기본 인자 단독 compose) 때문에 필수다.
    webSearchEnabled: Boolean = false,
    onToggleWebSearch: (Boolean) -> Unit = {}
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
                            // [WHY] 첨부만으로는 전송할 수 없다 — 텍스트가 비면 전송 버튼 자리에
                            // 마이크가 떠서 보낼 방법이 없는데 그 이유를 알 길이 없었다(AC3 감사).
                            if (textState.text.isBlank()) {
                                Text(
                                    androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.attachment_needs_text),
                                    color = KosmosTheme.colors.accent,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
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
                        if (textState.text.isBlank()) {
                            Text(
                                androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.attachment_needs_text),
                                color = KosmosTheme.colors.accent,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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

            // 웹 검색 허용 토글 — ON: 승인 없이 검색 허용 / OFF(기본): 모델에 툴 미노출 + 실행 차단
            IconButton(
                onClick = { onToggleWebSearch(!webSearchEnabled) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "WebSearchToggle",
                    tint = if (webSearchEnabled) KosmosTheme.colors.accent else KosmosTheme.colors.textMuted,
                    modifier = Modifier.size(22.dp)
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
