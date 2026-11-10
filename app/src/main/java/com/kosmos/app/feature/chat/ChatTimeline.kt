package com.kosmos.app.feature.chat

import android.content.ClipData
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kosmos.app.R
import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.MessageResult
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.feature.chat.result.ChatChip
import com.kosmos.app.feature.chat.result.ChatChipRules
import com.kosmos.app.feature.chat.result.ChatExtrasViewModel
import com.kosmos.app.feature.chat.result.ChatSummary
import com.kosmos.app.feature.chat.result.ResultCardState
import com.kosmos.app.ui.component.KosmosIcon
import com.kosmos.app.ui.component.LocalClock
import com.kosmos.app.ui.component.kosmosSurface
import com.kosmos.app.ui.theme.KosmosShapes
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.currentAppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 타임라인 한 행 — 회수 칩 라벨 요청 + [MessageWithDate].
 *
 * [WHY] 라이브 테일과 페이징 과거 두 items 블록이 같은 행 본문(칩 id 계산, 라벨 요청, 버블)을
 * 복제하고 있었다. 이웃([older]) 계산만 블록마다 다르므로 그것만 밖에서 받는다.
 */
@Composable
internal fun TimelineRow(
    message: ChatMessage,
    older: ChatMessage?,
    today: LocalDate,
    episodeChipLabels: Map<String, String?>,
    highlightStartAt: Long?,
    onEnsureChipLabel: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onCopy: (String) -> Unit,
    speakingMessageId: String? = null,
    onToggleSpeak: (ChatMessage) -> Unit = {},
    resultStates: Map<String, ResultCardState> = emptyMap(),
    onEnsureResult: (MessageResult) -> Unit = {},
    onResultClick: (ChatMessage) -> Unit = {},
    chips: List<ChatChip> = emptyList(),
    onChipClick: (ChatChip) -> Unit = {}
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
        result = message.result?.let { resultStates[ChatExtrasViewModel.keyOf(it)] },
        onResultClick = {
            ChatChipRules.recallEpisode(message.result)?.let(onOpenEpisode) ?: onResultClick(message)
        },
        chips = chips,
        onChipClick = onChipClick
    )
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
internal fun MessageWithDate(
    message: ChatMessage,
    older: ChatMessage?,
    today: LocalDate,
    onCopy: () -> Unit,
    recallChipLabel: String? = null,
    onRecallChipClick: () -> Unit = {},
    highlighted: Boolean = false,
    isSpeaking: Boolean = false,
    onToggleSpeak: () -> Unit = {},
    result: ResultCardState? = null,
    onResultClick: () -> Unit = {},
    chips: List<ChatChip> = emptyList(),
    onChipClick: (ChatChip) -> Unit = {}
) {
    val lang = currentAppLanguage()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val label = dateLabelIfBoundary(message, older, today, lang = lang)
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
                message.inputType == InputType.BRIEFING ->
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
                    animateResult = LocalClock.current.millis() - message.createdAt < NEW_RESULT_WINDOW_MS
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
internal fun SummaryTiles(
    summary: ChatSummary,
    onOpenTasks: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(44.dp)
            .kosmosSurface(shape = KosmosShapes.full),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummarySegment(
            R.drawable.ic_ph_bell,
            stringResource(R.string.chat_summary_next),
            summary.nextReminder ?: stringResource(R.string.chat_summary_none),
            stringResource(R.string.chat_summary_next_reminder_cd),
            onOpenTasks,
            Modifier.weight(1.3f)
        )
        SummaryDivider()
        SummarySegment(
            R.drawable.ic_ph_calendar_blank,
            stringResource(R.string.chat_summary_events),
            "${summary.todaySchedules}",
            stringResource(R.string.chat_summary_today_schedule),
            onOpenCalendar,
            Modifier.weight(1f)
        )
        SummaryDivider()
        SummarySegment(
            R.drawable.ic_ph_check_square,
            stringResource(R.string.chat_summary_tasks),
            "${summary.pendingTasks}",
            stringResource(R.string.chat_summary_pending_tasks_cd),
            onOpenTasks,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(Modifier.width(1.dp).height(20.dp).background(KosmosTheme.colors.border))
}

@Composable
private fun SummarySegment(
    @DrawableRes icon: Int,
    label: String,
    value: String,
    accessibleLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.chat_summary_open_cd, accessibleLabel),
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        KosmosIcon(icon, contentDescription = null, size = 18.dp, tint = KosmosTheme.colors.brandText)
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

/**
 * 목록의 [index] 로 이동해 그 항목 윗변을 화면 윗변에 맞춥니다 — 정렬 전에 [loadAround] 로 목표와 그 아래(더 최신) 이웃이 채워지길 기다린다.
 *
 * [WHY] 목표와 아래 이웃이 채워지기 전에 정렬하면, 이웃이 실제 높이로 커질 때 reverseLayout 목록이 바닥 쪽 첫 항목을 기준으로 자리를 지켜
 * 목표가 화면 위로 밀려났다 — 다음 대화의 긴 답에 내려앉았다(0.41.9 휴대폰, 0.41.10 M3, 0.41.11 휴대폰 진단으로 바로잡음).
 *
 * @param loadAround 목표·이웃이 채워지면 true(시간 안에 안 오면 false — 그때는 바닥 정렬로 둔다, 이동 자체는 이미 끝났다)
 */
internal suspend fun scrollToTimelineTarget(listState: LazyListState, index: Int, loadAround: suspend () -> Boolean) {
    listState.scrollToItem(index)
    if (!loadAround()) return
    // [WHY] 채워진 데이터가 실제 높이로 그려지는 것은 다음 프레임이다 — 그 전에 이동하면 커진 이웃(긴 답 4,541px)이 바닥 기준 칸과 목표 사이에서
    // 자라 목표가 화면 위로 밀려났다(0.41.11 휴대폰 진단). 높이가 반영된 뒤에 이동·정렬한다.
    withFrameNanos { }
    repeat(2) {
        listState.scrollToItem(index)
        withFrameNanos { }
        alignItemTop(listState, index)
        withFrameNanos { }
        // 남은 높이 변화로 목표가 다시 밀렸으면 한 번 더 잡는다.
        if (listState.layoutInfo.visibleItemsInfo.any { it.index == index }) return
    }
}

/** 점프 목표 아래(더 최신)로 화면 한 장을 채울 만큼 미리 채울 이웃 수 — 짧은 말풍선(약 100dp)도 화면을 덮는다. */
internal const val JUMP_NEIGHBORS = 12

/** 점프 전에 채워져 있어야 할 페이징 인덱스 — 목표와 그 아래(인덱스가 작은 쪽) 이웃. */
internal fun jumpLoadRange(pagedIndex: Int, neighbors: Int = JUMP_NEIGHBORS): IntRange =
    (pagedIndex - neighbors).coerceAtLeast(0)..pagedIndex

/**
 * [index] 항목의 윗변을 목록 화면 윗변에 맞춥니다 — 원문 대화가 처음부터 읽히게(0.36.2 사용자 요청).
 *
 * [WHY] reverseLayout 의 `scrollToItem` 은 항목을 시작 가장자리(**바닥**)에 놓는다. 아래에 화면 한 장만큼의 대화가 없으면(최근 기억)
 * 목록이 바닥에서 멈춘다 — 그때는 그 대화가 처음부터 최신까지 한 화면에 다 보인다(사용자 결정: 임시 여백을 두지 않는다).
 */
internal suspend fun alignItemTop(listState: LazyListState, index: Int) {
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
internal fun PlaceholderBubble() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .height(40.dp)
            .kosmosSurface(shape = RoundedCornerShape(16.dp))
    )
}

internal fun copyToClipboard(
    context: Context,
    scope: CoroutineScope,
    clipboard: Clipboard,
    snackbarHostState: SnackbarHostState,
    content: String
) {
    // [WHY] LocalClipboard 의 setClipEntry 는 suspend 다. 스낵바용 스코프에서 복사와 안내를
    // 순서대로 처리한다 — onLongPress 의 `() -> Unit` 콜백 계약은 그대로 유지된다.
    scope.launch {
        clipboard.setClipEntry(
            ClipEntry(
                ClipData.newPlainText(context.getString(R.string.chat_clipboard_label), content)
            )
        )
        snackbarHostState.showSnackbar(context.getString(R.string.chat_message_copied))
    }
}

/**
 * [message]가 [older]와 다른 날이면(그 날의 첫 메시지) 구분선 라벨을 돌려줍니다.
 * [WHY] [today] 는 인자다 — 예전에는 여기서 벽시계를 읽어 판정이 실행 시각에 묶였다(AGENTS §2-④).
 */
internal fun dateLabelIfBoundary(
    message: ChatMessage,
    older: ChatMessage?,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    lang: AppLanguage = AppLanguage.KO
): String? {
    if (older == null) return null
    val date = Instant.ofEpochMilli(message.createdAt).atZone(zone).toLocalDate()
    val olderDate = Instant.ofEpochMilli(older.createdAt).atZone(zone).toLocalDate()
    if (date == olderDate) return null
    return IsoDateTimeParser.dayLabelKorean(date, today, lang)
}
