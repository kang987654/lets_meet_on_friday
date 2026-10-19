package com.kosmos.app.feature.chat

import androidx.compose.ui.res.painterResource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.kosmos.app.assistant.orchestrator.AttachedDocumentBlock
import com.kosmos.app.ui.component.kosmosSurface

import android.net.Uri
import androidx.compose.ui.platform.LocalContext
// [WHY] ChatScreen.kt(1,200여 줄, 12개 컴포저블 동거)를 응집 단위로 분리했다
// (2026-08-15, MVP 감사 refactor-ui) — 순수 이동이며 동작 변경이 없다. 이 파일: 말풍선·구분선·타이핑 표시·첨부 썸네일.

@Composable
internal fun DateSeparator(label: String) {
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
    // [WHY] 첨부 문서는 모델 입력용 구분자째 저장된다 — 원문 대신 접힌 칩으로 그리고 눌러서 펼친다(0.36.0 D6).
    val attachedBody = remember(text) { AttachedDocumentBlock.bodyOrNull(text) }
    var expanded by remember(text) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(onClick = { if (attachedBody != null) expanded = !expanded }, onLongClick = onLongPress)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(KosmosTheme.colors.brandContainer, KosmosTheme.colors.brandContainerEnd)
                    ),
                    shape = UserBubbleShape
                )
                .border(width = 1.dp, color = KosmosTheme.colors.brand.copy(alpha = 0.25f), shape = UserBubbleShape)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                val inputLabel = when (inputType) {
                    com.kosmos.app.domain.model.InputType.IMAGE -> com.kosmos.app.R.drawable.ic_ph_image to "첨부된 이미지"
                    com.kosmos.app.domain.model.InputType.VOICE -> com.kosmos.app.R.drawable.ic_ph_microphone to "음성 메시지"
                    else -> null
                }
                inputLabel?.let { (icon, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.kosmos.app.ui.component.KosmosIcon(icon, contentDescription = null, size = com.kosmos.app.ui.component.IconSize.inline, tint = KosmosTheme.colors.textMuted, modifier = Modifier.padding(end = 4.dp))
                        Text(text = label, color = KosmosTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                }
                if (attachedBody != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.kosmos.app.ui.component.KosmosIcon(com.kosmos.app.R.drawable.ic_ph_paperclip, contentDescription = null, size = com.kosmos.app.ui.component.IconSize.inline, tint = KosmosTheme.colors.textSecondary)
                        Text(
                            text = " 첨부 문서 · ${attachedBody.length}자",
                            color = KosmosTheme.colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        com.kosmos.app.ui.component.KosmosIcon(if (expanded) com.kosmos.app.R.drawable.ic_ph_caret_up else com.kosmos.app.R.drawable.ic_ph_caret_down, contentDescription = if (expanded) "접기" else "펼치기", size = com.kosmos.app.ui.component.IconSize.inline, tint = KosmosTheme.colors.textSecondary)
                    }
                    if (expanded) {
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(text = attachedBody, color = KosmosTheme.colors.textPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(
                        text = text,
                        color = KosmosTheme.colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
    // [WHY] 회수 칩(🧠, 시안 A′ P3 투명성): 이 답변이 과거 에피소드 기억을 참고했음을 표시한다.
    // 기본값 null = 미렌더 — E2E(단독 compose)와 기존 버블 어서션이 그대로 유지된다.
    recallChipLabel: String? = null,
    onRecallChipClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    // [WHY] 말풍선 재생 버튼(0.29.0 D3) — null 이면 미렌더. 스트리밍 중 말풍선·E2E 단독 compose 는 기본값 그대로.
    isSpeaking: Boolean = false,
    onToggleSpeak: (() -> Unit)? = null,
    // [WHY] 결과 행(0.38.2) — 카드 안 문장 아래. null 이면 글만(옛 메시지·스트리밍·E2E 단독 compose).
    result: com.kosmos.app.feature.chat.result.ResultCardState? = null,
    onResultClick: (() -> Unit)? = null,
    // [WHY] 상황 칩(0.38.2 M6) — 마지막 비서 답에만 화면이 넘긴다. 읽기 버튼 아래.
    chips: List<com.kosmos.app.feature.chat.result.ChatChip> = emptyList(),
    onChipClick: (com.kosmos.app.feature.chat.result.ChatChip) -> Unit = {}
) {
    var isThinkingExpanded by remember { mutableStateOf(false) }
    val recallInCard = recallChipLabel != null && result?.kind == com.kosmos.app.domain.model.MessageResult.Kind.MEMORY_RECALL

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            // [WHY] 결과 행이 있으면 넓힌다 — 값·이름·날짜가 280dp 에서는 꺾였다(0.38.2 M3 렌더). 글만 있는 답은 그대로.
            modifier = Modifier.widthIn(max = if (result != null) 340.dp else 280.dp)
        ) {
            if (thinkingProcess != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .kosmosSurface(
                            backgroundColor = KosmosTheme.colors.surfaceSunk,
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                        )
                        .clickable { isThinkingExpanded = !isThinkingExpanded }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.kosmos.app.ui.component.KosmosIcon(com.kosmos.app.R.drawable.ic_ph_lightbulb, contentDescription = null, size = com.kosmos.app.ui.component.IconSize.inline, tint = KosmosTheme.colors.textSecondary, modifier = Modifier.padding(end = 6.dp))
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
                            .kosmosSurface(
                                backgroundColor = KosmosTheme.colors.surface,
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .kosmosSurface(shape = shape)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides KosmosTheme.colors.textPrimary
                ) {
                    ProvideTextStyle(
                        value = MaterialTheme.typography.bodyLarge.copy(color = KosmosTheme.colors.textPrimary)
                    ) {
                        RichText {
                            Markdown(content = text)
                        }
                    }
                }
                if (result != null) {
                    com.kosmos.app.feature.chat.result.ResultRow(result, onClick = onResultClick)
                    if (result.evidence.isNotEmpty()) {
                        androidx.compose.material3.HorizontalDivider(color = KosmosTheme.colors.border)
                        result.evidence.forEachIndexed { index, line ->
                            EvidenceLine(line)
                            // [WHY] 회수 칩을 근거 줄로 접는다 — 카드 밖 칩과 근거 줄이 같은 "어디서 찾았나"를 두 번 말했다(사용자 지적, 0.38.2 M3).
                            if (index == 0 && recallInCard) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClickLabel = "대화 열기", onClick = onRecallChipClick)
                                        .padding(vertical = 2.dp)
                                ) {
                                    com.kosmos.app.ui.component.KosmosIcon(com.kosmos.app.R.drawable.ic_ph_chat_text, contentDescription = null, size = 16.dp, tint = KosmosTheme.colors.brandText, modifier = Modifier.padding(end = 8.dp))
                                    Text(
                                        "함께 본 대화: $recallChipLabel",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = KosmosTheme.colors.brandText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    com.kosmos.app.ui.component.KosmosIcon(com.kosmos.app.R.drawable.ic_ph_caret_right, contentDescription = null, size = 16.dp, tint = KosmosTheme.colors.brandText)
                                }
                            }
                        }
                    }
                }
            }

            // [WHY] 이 답변이 네트워크로 나갔는지를 사용자가 볼 수 있어야 한다. 프라이버시
            // 우선 원칙에서 웹 검색은 토글로 명시 허용하는 동작이므로, 실제로 쓰인 턴을
            // 표시하지 않으면 토글의 의미가 절반만 남는다.
            if (searchUsed) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, top = 4.dp)) {
                    com.kosmos.app.ui.component.KosmosIcon(com.kosmos.app.R.drawable.ic_ph_globe, contentDescription = null, size = 16.dp, tint = KosmosTheme.colors.textMuted, modifier = Modifier.padding(end = 4.dp))
                    Text(
                        text = "위키백과 검색 결과를 참고했어요",
                        color = KosmosTheme.colors.textMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // 회수 칩 — searchUsed 표기와 같은 원칙: 답변의 출처(기억)를 보이게 한다. 기억 카드가 있으면 카드 근거 줄로 들어간다.
            if (recallChipLabel != null && !recallInCard) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 14.dp, top = 4.dp)
                        .kosmosSurface(shape = RoundedCornerShape(99.dp))
                        .clickable(onClick = onRecallChipClick)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    com.kosmos.app.ui.component.KosmosIcon(com.kosmos.app.R.drawable.ic_ph_clock_counter_clockwise, contentDescription = null, size = 16.dp, tint = KosmosTheme.colors.textSecondary, modifier = Modifier.padding(end = 4.dp))
                    Text(
                        text = "기억에서 — $recallChipLabel",
                        color = KosmosTheme.colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (onToggleSpeak != null) {
                IconButton(onClick = onToggleSpeak, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(id = if (isSpeaking) com.kosmos.app.R.drawable.ic_ph_stop_fill else com.kosmos.app.R.drawable.ic_ph_speaker_high),
                        contentDescription = if (isSpeaking) "읽기 멈추기" else "답변 읽기",
                        tint = if (isSpeaking) KosmosTheme.colors.brandText else KosmosTheme.colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (chips.isNotEmpty()) {
                ChatChipRow(chips, onChipClick, Modifier.padding(top = 4.dp))
            }
        }
    }
}
/**
 * 기억 카드의 근거 한 줄 — 아이콘으로 출처 종류를 보이고, 메모와 다른 값이 있었다는 줄은 옅은 주의 면으로 따로 띄운다(0.38.2 M7b).
 */
@Composable
private fun EvidenceLine(line: com.kosmos.app.feature.chat.result.Evidence) {
    val colors = KosmosTheme.colors
    val conflict = line.kind == com.kosmos.app.feature.chat.result.Evidence.Kind.CONFLICT
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (conflict) {
            Modifier.fillMaxWidth().background(colors.warningText.copy(alpha = 0.10f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp)
        } else Modifier
    ) {
        val icon = when (line.kind) {
            com.kosmos.app.feature.chat.result.Evidence.Kind.NOTE -> com.kosmos.app.R.drawable.ic_ph_note
            com.kosmos.app.feature.chat.result.Evidence.Kind.CONVERSATION -> com.kosmos.app.R.drawable.ic_ph_chat_text
            com.kosmos.app.feature.chat.result.Evidence.Kind.CONFLICT -> com.kosmos.app.R.drawable.ic_ph_warning_circle
        }
        com.kosmos.app.ui.component.KosmosIcon(icon, contentDescription = null, size = 16.dp, tint = if (conflict) colors.warningText else colors.textMuted, modifier = Modifier.padding(end = 8.dp))
        Text(line.text, style = MaterialTheme.typography.labelMedium, color = if (conflict) colors.textPrimary else colors.textSecondary)
    }
}

/**
 * 상황 칩 줄(0.38.2 M6) — 알약 모양 면, 진한 하늘 글자. 넘치면 다음 줄로.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChatChipRow(
    chips: List<com.kosmos.app.feature.chat.result.ChatChip>,
    onChipClick: (com.kosmos.app.feature.chat.result.ChatChip) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { chip ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .kosmosSurface(shape = RoundedCornerShape(99.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onChipClick(chip) }
                    .padding(start = if (chip.icon != null) 12.dp else 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
            ) {
                chip.icon?.let {
                    com.kosmos.app.ui.component.KosmosIcon(it, contentDescription = null, size = 16.dp, tint = KosmosTheme.colors.brandText, modifier = Modifier.padding(end = 6.dp))
                }
                Text(text = chip.label, color = KosmosTheme.colors.brandText, style = MaterialTheme.typography.labelLarge)
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
        // [WHY] 응답 대기도 비서 카드와 같은 모양(받은 쪽 아래 모서리 4) — 곧 그 자리에 답 카드가 온다(0.38.2 M7).
        Box(
            modifier = Modifier
                .kosmosSurface(shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp))
        ) {
            com.kosmos.app.ui.component.TwinkleStars()
        }
    }
}
/**
 * 첨부 이미지 썸네일 (C-4).
 * [WHY] 외부 이미지 로딩 라이브러리를 추가하지 않고, `inSampleSize` 다운샘플 디코딩을
 * IO 디스패처에서 수행해 메인 스레드 부담과 메모리 사용을 함께 억제한다.
 */
@Composable
internal fun AttachmentThumbnail(uri: Uri, sizeDp: Int = 48) {
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
            .background(KosmosTheme.colors.surfaceSunk)
    ) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "첨부 이미지",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** 사용자 말풍선 모양 — 오른쪽 아래 모서리만 뾰족하다. */
private val UserBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
