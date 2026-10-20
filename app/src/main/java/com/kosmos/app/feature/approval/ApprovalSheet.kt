package com.kosmos.app.feature.approval

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.remember
import com.kosmos.app.assistant.approval.ApprovalCoordinator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kosmos.app.R
import com.kosmos.app.assistant.approval.ApprovalPreview
import com.kosmos.app.assistant.approval.ApprovalRequest
import com.kosmos.app.domain.model.ProfileSuggestion
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.ui.component.IconSize
import com.kosmos.app.ui.component.KosmosButton
import com.kosmos.app.ui.component.KosmosIcon
import com.kosmos.app.ui.component.rememberToday
import com.kosmos.app.ui.theme.KosmosShapes
import com.kosmos.app.ui.theme.KosmosTheme
import java.time.LocalDate
import java.time.ZoneId

/**
 * 공통 승인 시트가 보일 내용 — 무엇을 하는지(종류) · 대상 · 언제 · 메모 · 안내 줄 · 버튼 글자.
 *
 * 일정·알림·메모 승인과 프로필 제안이 같은 모양을 쓴다(0.38.2 M5, 캔버스 C382Approve). 표기는 [IsoDateTimeParser] 단일 출처(§4-7).
 */
data class ApprovalSheetContent(
    @DrawableRes val icon: Int,
    val label: String,
    val title: String,
    val sub: String? = null,
    val value: String? = null,
    val valueSub: String? = null,
    val note: String? = null,
    val hint: String? = null,
    val rejectLabel: String = "취소",
    val approveLabel: String = "승인"
) {
    companion object {
        /** 툴 승인 요청 → 시트 내용. 미리보기가 없는 요청은 제목·설명만. */
        fun of(request: ApprovalRequest, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): ApprovalSheetContent {
            val p = request.preview ?: return ApprovalSheetContent(R.drawable.ic_ph_shield_check, request.title, request.description)
            val day = p.atIso?.let { dayOf(it, today, zone) }
            val time = p.atIso?.let { IsoDateTimeParser.toDisplayTimeKorean(it, zone) }
            return when (p.kind) {
                ApprovalPreview.Kind.SCHEDULE -> ApprovalSheetContent(
                    icon = R.drawable.ic_ph_calendar_blank_duotone, label = "일정 추가", title = p.subject,
                    sub = day, value = time,
                    valueSub = p.endIso?.let { IsoDateTimeParser.toDisplayTimeKorean(it, zone) }?.let { "${it}까지" }, note = p.note?.takeIf { it.isNotBlank() },
                    hint = "앱 일정과 기기 캘린더에 함께 추가돼요.", approveLabel = "추가하기"
                )
                ApprovalPreview.Kind.REMINDER -> ApprovalSheetContent(
                    icon = R.drawable.ic_ph_bell_duotone, label = "알림 등록", title = p.subject, sub = day, value = time,
                    hint = "정한 시각에 알림이 울려요.", approveLabel = "등록하기"
                )
                ApprovalPreview.Kind.MEMORY -> ApprovalSheetContent(
                    icon = R.drawable.ic_ph_clock_counter_clockwise_duotone, label = "메모 저장", title = p.subject,
                    hint = "저장하면 비서가 다음 대화부터 참고해요.", approveLabel = "저장하기"
                )
            }
        }

        /** 자동 추출 프로필 제안 → 시트 내용. 남은 제안 수는 종류 줄에. */
        fun of(suggestion: ProfileSuggestion, remaining: Int): ApprovalSheetContent = ApprovalSheetContent(
            icon = R.drawable.ic_ph_user_circle,
            label = if (remaining > 0) "프로필 저장 · 외 ${remaining}건" else "프로필 저장",
            title = suggestion.value, sub = suggestion.key,
            hint = "대화에서 찾은 항목이에요. 저장하면 비서가 항상 기억해요.",
            rejectLabel = "무시", approveLabel = "프로필에 저장"
        )

        private fun dayOf(iso: String, today: LocalDate, zone: ZoneId): String? {
            val date = IsoDateTimeParser.toLocalDate(iso, zone) ?: return null
            return if (date == today) "오늘" else IsoDateTimeParser.toDisplayDateKorean(iso, zone)
        }
    }
}

/**
 * 공통 승인 시트(0.38.2 M5) — 머리(아이콘 · 종류 · 대상 · 큰 시각), 메모 면, 안내 줄, [거절][승인].
 *
 * 버튼·머리 모양은 알림 수정 시트와 같다. 주 버튼은 하나(`accent`).
 *
 * @param footnote 경고 한 줄(정확 알람 불가 등) — 안내 줄 위에 경고색으로.
 * @param timeoutKey 제한 시간이 있는 승인이면 그 회차 키 — 바뀌면 [거절] 버튼의 차오름이 처음부터 다시 시작한다. null 이면 없음(프로필 제안).
 * @param onDismiss 끌어내리거나 바깥을 눌렀을 때. 툴 승인은 거절과 같고, 프로필 제안은 "나중에"(제안은 남는다).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheetLayout(
    content: ApprovalSheetContent,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit = onReject,
    footnote: String? = null,
    timeoutKey: Any? = null,
    onFootnoteAction: (() -> Unit)? = null
) {
    val colors = KosmosTheme.colors
    // [WHY] 툴 승인은 제한 시간이 지나면 저절로 취소된다 — 문구 대신 [거절] 버튼이 남은 시간만큼 차올라 "곧 취소됨"을 보인다(사용자 결정 2026-10-19).
    val elapsed = remember(timeoutKey) { Animatable(0f) }
    if (timeoutKey != null) {
        LaunchedEffect(timeoutKey) {
            elapsed.animateTo(1f, tween(ApprovalCoordinator.APPROVAL_TIMEOUT_MS.toInt(), easing = LinearEasing))
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = KosmosShapes.sheet,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KosmosIcon(content.icon, contentDescription = null, size = IconSize.large, tint = colors.brandText)
                Column(Modifier.weight(1f)) {
                    Text(content.label, style = MaterialTheme.typography.labelMedium, color = colors.brandText)
                    Text(
                        content.title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary,
                        maxLines = 6, overflow = TextOverflow.Ellipsis
                    )
                    content.sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
                }
                content.value?.let { value ->
                    // [WHY] 끝 시각은 큰 시각 아래에 — 왼쪽 날짜 줄에 붙이면 다른 날 일정에서 줄이 넘쳤다.
                    Column(horizontalAlignment = Alignment.End) {
                        Text(value, style = MaterialTheme.typography.displaySmall, color = colors.textPrimary)
                        content.valueSub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
                    }
                }
            }
            content.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceSunk, KosmosShapes.md)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 화면 읽기에는 남은 초를 알린다 — 차오르는 면은 눈으로만 보인다(impeccable 점검 P2).
                val secondsLeft by remember(timeoutKey) {
                    derivedStateOf { ((1f - elapsed.value) * ApprovalCoordinator.APPROVAL_TIMEOUT_MS / 1000).toInt() }
                }
                KosmosButton(
                    content.rejectLabel, onReject,
                    Modifier.weight(1f).then(if (timeoutKey != null) Modifier.semantics { stateDescription = "${secondsLeft}초 뒤 자동 취소" } else Modifier),
                    progress = if (timeoutKey != null) ({ elapsed.value }) else null
                )
                KosmosButton(content.approveLabel, onApprove, Modifier.weight(1f), accent = true)
            }
            if (footnote != null) {
                // [WHY] 경고가 있으면 기본 안내("정한 시각에 울려요")를 숨긴다 — 둘이 서로 모순됐다(impeccable 점검 P1, 0.38.2 M8).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(footnote, style = MaterialTheme.typography.labelMedium, color = colors.warningText, modifier = Modifier.weight(1f))
                    if (onFootnoteAction != null) {
                        TextButton(onClick = onFootnoteAction) { Text("설정에서 켜기", style = MaterialTheme.typography.labelLarge, color = colors.brandText) }
                    }
                }
            } else {
                content.hint?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.textMuted) }
            }
        }
    }
}

/**
 * 툴 승인(일정·알림·메모) 시트 — 끌어내리면 거절과 같다(60초 시간 초과와 같은 경로, `ApprovalCoordinator`).
 */
@Composable
fun ApprovalSheet(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    footnote: String? = null,
    timeoutKey: Any? = null,
    onFootnoteAction: (() -> Unit)? = null
) {
    val today = rememberToday()
    ApprovalSheetLayout(ApprovalSheetContent.of(request, today), onApprove, onReject, footnote = footnote, timeoutKey = timeoutKey, onFootnoteAction = onFootnoteAction)
}

/**
 * 자동 추출 프로필 제안 시트 — 끌어내리면 [onLater](이번 화면에서만 접고 제안은 남긴다). 무시는 버튼으로만.
 */
@Composable
fun ProfileSuggestionSheet(
    suggestion: ProfileSuggestion,
    remaining: Int,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onLater: () -> Unit
) {
    ApprovalSheetLayout(ApprovalSheetContent.of(suggestion, remaining), onAccept, onReject, onDismiss = onLater)
}
