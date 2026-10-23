package com.kosmos.app.feature.chat

import com.kosmos.app.feature.splash.splashAnchor
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import com.kosmos.app.ui.theme.KosmosTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosmos.app.ui.component.kosmosSurface

import com.kosmos.app.platform.share.SharedInput
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
    onToggleWebSearch: (Boolean) -> Unit = {},
    // 문서 파일(xlsx·docx·hwpx·PDF)을 읽는 중 — 기본값은 E2E 계약(단독 compose) 때문에 필수다(0.35.0 M4).
    attachmentExtracting: Boolean = false,
    // 녹음 중 소리 크기(0..1) — 파형(0.38.4 M5). 기본값은 E2E 계약(단독 compose) 때문에.
    recordingLevel: () -> Float = { 0f }
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
            // [WHY] M3 Scaffold 는 bottomBar 슬롯의 인셋을 슬롯 자신이 처리한다고 가정한다
            // (NavigationBar 는 내장 — M2-2 에서 그 셸 Scaffold 를 제거하며 드러났다).
            // 내비 제스처 바와 키보드 위로 입력바를 올린다. Robolectric 에서는 인셋이 0 이라
            // E2E 계약(셀렉터·단독 compose)에 영향 없다.
            .navigationBarsPadding()
            .imePadding()
            .splashAnchor(com.kosmos.app.feature.splash.SplashAnchor.INPUT_BAR)
    ) {
        when (sharedInput) {
            is com.kosmos.app.platform.share.SharedInput.Image -> AttachmentPreview(
                needsText = textState.text.isBlank(),
                onClear = onClearSharedInput,
                leading = { AttachmentThumbnail(uri = sharedInput.uri) },
                title = androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.image_attached),
                subtitle = androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.image_size_kb, sharedInput.sizeBytes / 1024)
            )
            // [WHY] "Document Attached" 는 E2E 셀렉터다(MultimodalChatE2ETest) —
            // "Attach"/"Send" contentDescription 과 같은 불변 계약이라 한글화에서 제외.
            is com.kosmos.app.platform.share.SharedInput.Document -> AttachmentPreview(
                needsText = textState.text.isBlank(),
                onClear = onClearSharedInput,
                title = "Document Attached",
                subtitle = if (sharedInput.truncated) "${sharedInput.fileName} · 앞부분만" else sharedInput.fileName
            )
            else -> if (attachmentExtracting) {
                androidx.compose.material3.Text(
                    text = "문서를 읽는 중…",
                    color = KosmosTheme.colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .kosmosSurface(
                    shape = RoundedCornerShape(28.dp),
                    borderColor = KosmosTheme.colors.border
                )
                // [WHY] 아이콘 버튼 높이를 48dp(DESIGN·M3 누르는 영역)로 — 폭은 40 그대로 두어 아이콘 간격이 벌어지지 않게 하고(사용자 지적),
                // 세로 여백을 줄여 입력바 높이도 그대로다(0.38.3 M0, 0.38.2 이월). 왼쪽 여백 6 — 아이콘을 왼쪽으로 붙인다(사용자 요청).
                .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onAttachClick() },
                enabled = !isLoading,
                modifier = Modifier.size(width = 40.dp, height = 48.dp)
            ) {
                Icon(
                    painter = painterResource(id = com.kosmos.app.R.drawable.ic_ph_paperclip),
                    contentDescription = "Attach",
                    tint = if (!isLoading) KosmosTheme.colors.textSecondary else KosmosTheme.colors.textMuted,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 웹 검색 허용 토글 — ON: 승인 없이 검색 허용 / OFF(기본): 모델에 툴 미노출 + 실행 차단
            // [WHY] 켜짐·꺼짐을 색으로만 보였다 — 토글 역할과 상태 설명(0.38.3 점검 P2). 설명 "웹 검색"은 그대로.
            IconButton(
                onClick = { onToggleWebSearch(!webSearchEnabled) },
                modifier = Modifier.size(width = 40.dp, height = 48.dp)
                    .semantics { stateDescription = if (webSearchEnabled) "켜짐" else "꺼짐" }
            ) {
                Icon(
                    painter = painterResource(id = com.kosmos.app.R.drawable.ic_ph_globe),
                    contentDescription = "웹 검색",
                    tint = if (webSearchEnabled) KosmosTheme.colors.brandText else KosmosTheme.colors.textMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                val reduced = com.kosmos.app.ui.component.rememberReducedMotion()
                if (isRecording && !reduced) {
                    // [WHY] 듣는 중에는 지나온 소리가 흐르는 파형 — 글자만으로는 실제로 들리는지 알 수 없었다(0.38.4 M5, 사용자 선택 B). 화면 읽기에는 같은 말을 알린다.
                    RecordingWave(recordingLevel, Modifier.fillMaxWidth().height(28.dp).semantics {
                        contentDescription = "듣고 있어요"
                        liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                    })
                } else if (textState.text.isEmpty()) {
                    // [WHY] 입력란이 잠긴 이유를 그 자리에서 말한다 — 답을 쓰는 중·듣는 중에도 "메시지를 입력하세요"가 떠 있었다(0.38.2 M7).
                    Text(
                        text = when {
                            isRecording -> "듣고 있어요…"
                            isLoading -> "답을 쓰는 중이에요…"
                            else -> androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.chat_input_hint)
                        },
                        color = if (isRecording) KosmosTheme.colors.textSecondary else KosmosTheme.colors.textMuted,
                        // 상태가 바뀌면(듣는 중·답을 쓰는 중·끝) 화면 읽기가 알린다(0.38.3 점검 P2).
                        modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                BasicTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = KosmosTheme.colors.textPrimary),
                    cursorBrush = SolidColor(KosmosTheme.colors.brand),
                    enabled = !isLoading,
                    maxLines = 5
                )
            }
            
            // [WHY] 생성 중에는 전송/마이크 대신 정지 버튼을 노출한다. 설명 문자열은 E2E 계약인
            // "Attach"/"Send" 만 영어로 두고 나머지는 한국어다(TalkBack 이 그대로 읽는다) — 정지
            // 버튼 "응답 중단"과 녹음 버튼 "녹음 중지"는 서로 겹치지 않게 다른 말을 쓴다.
            val inputAction = when {
                isLoading -> InputAction.STOP
                textState.text.isNotBlank() -> InputAction.SEND
                else -> InputAction.MIC
            }

            androidx.compose.animation.Crossfade(
                targetState = inputAction,
                // 보이는 원은 40, 높이만 48 — 위아래 4dp 씩 여유.
                modifier = Modifier.size(width = 40.dp, height = 48.dp).padding(vertical = 4.dp),
                animationSpec = com.kosmos.app.ui.theme.Motion.short(),
                label = "send_mic_transition"
            ) { action ->
                if (action == InputAction.STOP) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(KosmosTheme.colors.surface)
                            .border(1.dp, KosmosTheme.colors.brand, shape = androidx.compose.foundation.shape.CircleShape)
                            .clickable { onStopGeneration() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = com.kosmos.app.R.drawable.ic_ph_stop_fill),
                            contentDescription = "답 멈추기",
                            tint = KosmosTheme.colors.brandText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (action == InputAction.SEND) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            // [WHY] 주 버튼과 같은 채도 낮춘 하늘(brandSoft) — 진한 brand 면은 0.38.1 M6 에서 너무 진하다는 지적으로 바꾼 기준이다.
                            .background(KosmosTheme.colors.brandSoft)
                            .clickable(enabled = !isLoading) {
                                onSend(textState.text)
                                textState = TextFieldValue("")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = com.kosmos.app.R.drawable.ic_ph_arrow_up),
                            contentDescription = "Send",
                            tint = KosmosTheme.colors.onBrandSoft,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    val micBg = if (isRecording) KosmosTheme.colors.dangerText.copy(alpha = 0.2f) else KosmosTheme.colors.surface
                    val micBorder = if (isRecording) KosmosTheme.colors.dangerText else KosmosTheme.colors.border
                    val micIconColor = if (isRecording) KosmosTheme.colors.dangerText else KosmosTheme.colors.textSecondary
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
                            painter = painterResource(id = if (isRecording) com.kosmos.app.R.drawable.ic_ph_stop_fill else com.kosmos.app.R.drawable.ic_ph_microphone),
                            contentDescription = if (isRecording) "녹음 중지" else "음성 입력",
                            tint = micIconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 입력바 위의 첨부 미리보기 한 줄 — 이미지·문서가 같은 뼈대를 쓴다.
 *
 * [WHY] 두 분기가 같은 글래스 박스·닫기 버튼·"글도 적어 주세요" 안내를 복제하고 있었다.
 * [WHY] 첨부만으로는 전송할 수 없다 — 텍스트가 비면 전송 버튼 자리에 마이크가 떠서 보낼 방법이
 * 없는데 그 이유를 알 길이 없었다(AC3 감사). 그래서 [needsText] 일 때 안내를 붙인다.
 * 닫기는 "X" 글자 대신 아이콘 + 한국어 설명이다(TalkBack 이 "엑스"로 읽었다).
 */
@Composable
private fun AttachmentPreview(
    needsText: Boolean,
    onClear: () -> Unit,
    title: String,
    subtitle: String,
    leading: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .kosmosSurface(shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leading != null) {
                    leading()
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    Text(title, color = KosmosTheme.colors.brandText, style = MaterialTheme.typography.bodyMedium)
                    Text(subtitle, color = KosmosTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                    if (needsText) {
                        Text(
                            androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.attachment_needs_text),
                            color = KosmosTheme.colors.brandText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            IconButton(onClick = onClear) {
                Icon(
                    painter = painterResource(id = com.kosmos.app.R.drawable.ic_ph_x),
                    contentDescription = "첨부 취소",
                    tint = KosmosTheme.colors.textMuted
                )
            }
        }
    }
}

/**
 * 녹음 중 파형 — 80ms 마다 소리 크기를 하나 받아 오른쪽에 붙이고 왼쪽으로 흘린다(최근이 오른쪽). 조용하면 낮은 회색 점.
 * 녹음하는 동안만 돈다(DESIGN Motion "계속 도는 애니메이션은 진행 중일 때만").
 */
@Composable
private fun RecordingWave(level: () -> Float, modifier: Modifier = Modifier) {
    val history = remember { androidx.compose.runtime.mutableStateListOf<Float>().apply { repeat(WAVE_BARS) { add(0f) } } }
    LaunchedEffect(Unit) {
        while (true) {
            history.removeAt(0)
            history.add(level().coerceIn(0f, 1f))
            kotlinx.coroutines.delay(WAVE_TICK_MS)
        }
    }
    val active = KosmosTheme.colors.brand
    val quiet = KosmosTheme.colors.textMuted.copy(alpha = 0.4f)
    androidx.compose.foundation.Canvas(modifier.padding(end = 8.dp)) {
        val w = 3.dp.toPx()
        val gap = (size.width - w * WAVE_BARS) / (WAVE_BARS - 1)
        history.forEachIndexed { i, lv ->
            val h = (size.height * (0.12f + 0.88f * lv)).coerceAtLeast(w)
            drawRoundRect(
                if (lv > 0.05f) active else quiet,
                androidx.compose.ui.geometry.Offset(i * (w + gap), (size.height - h) / 2),
                androidx.compose.ui.geometry.Size(w, h),
                androidx.compose.ui.geometry.CornerRadius(w / 2)
            )
        }
    }
}

private const val WAVE_BARS = 24
private const val WAVE_TICK_MS = 80L
