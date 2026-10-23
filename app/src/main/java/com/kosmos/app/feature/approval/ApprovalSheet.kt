package com.kosmos.app.feature.approval

import androidx.compose.foundation.verticalScroll
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.kosmos.app.ui.component.SheetHeader
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameMillis
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

        /**
         * 자동 추출 프로필 제안 → 시트 내용. 한 건이면 그 항목을 머리에, 여러 건이면 머리는 개수만 — 항목은 시트의 체크 목록에 보인다.
         *
         * [WHY] 여러 건을 "외 N건"으로만 보이면 저장 버튼이 안 보이는 항목까지 저장하는 것처럼 읽혔다(0.38.5 impeccable P1).
         */
        fun of(suggestions: List<ProfileSuggestion>): ApprovalSheetContent {
            val single = suggestions.singleOrNull()
            return ApprovalSheetContent(
                icon = R.drawable.ic_ph_user_circle,
                label = "프로필 저장",
                title = single?.value ?: "대화에서 찾은 항목 ${suggestions.size}개",
                sub = single?.key,
                hint = if (single != null) "대화에서 찾은 항목이에요. 저장하면 비서가 항상 기억해요."
                else "고른 항목만 저장하고 나머지는 무시해요. 저장하면 비서가 항상 기억해요.",
                rejectLabel = if (single != null) "무시" else "모두 무시",
                approveLabel = "프로필에 저장"
            )
        }

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
 * @param timeoutKey 제한 시간이 있는 승인이면 그 회차 키 — 바뀌면 남은 시간 선이 처음부터 다시 시작한다. null 이면 없음(프로필 제안).
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
    onFootnoteAction: (() -> Unit)? = null,
    approveEnabled: Boolean = true,
    // 머리와 버튼 사이에 들어가는 본문(프로필 제안 체크 목록).
    body: (@Composable () -> Unit)? = null
) {
    val colors = KosmosTheme.colors
    // [WHY] 툴 승인은 제한 시간이 지나면 저절로 취소된다 — 버튼 줄 위 얇은 선이 남은 시간만큼 줄어든다. [거절] 면을 채우던 방식은
    // 보조 버튼이 주 버튼만큼 무거워 보였다(0.38.5 impeccable P2 → 0.38.6 M2 사용자 결정).
    // [WHY] 남은 시간은 프레임 시계로 잰다 — `Animatable.animateTo(tween(60초))` 는 시스템 애니메이션 배율을 따라 "애니메이션 제거"면 곧바로 차서
    // 화면 읽기가 "0초 뒤 자동 취소"라고 했다(0.38.3 점검 P2). 실제 제한 시간은 [ApprovalCoordinator] 가 따로 센다. 애니메이션 제거면 1초에 한 번만 갱신(조용한 단계 채움).
    val reduceMotion = com.kosmos.app.ui.component.rememberReducedMotion()
    var elapsedFraction by remember(timeoutKey) { mutableFloatStateOf(0f) }
    if (timeoutKey != null) {
        LaunchedEffect(timeoutKey) {
            val start = withFrameMillis { it }
            while (elapsedFraction < 1f) {
                if (reduceMotion) kotlinx.coroutines.delay(1_000)
                withFrameMillis { now ->
                    elapsedFraction = ((now - start).toFloat() / ApprovalCoordinator.APPROVAL_TIMEOUT_MS).coerceAtMost(1f)
                }
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = KosmosShapes.sheet,
        containerColor = colors.surface
    ) {
        // [WHY] 가로 화면·큰 글자에서 [승인]/[미루기]가 화면 밖으로 잘렸다 — 승인은 시간이 지나면 자동 취소라 누를 수 없게 됐다(0.38.3 M6 점검 P1).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SheetHeader(content.icon, content.label, content.title, sub = content.sub, value = content.value, valueSub = content.valueSub)
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
            body?.invoke()
            if (timeoutKey != null) {
                val track = colors.border
                val fill = colors.brand
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .drawBehind {
                            drawRect(track)
                            drawRect(fill, size = size.copy(width = size.width * (1f - elapsedFraction)))
                        }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 화면 읽기에는 남은 초를 [거절] 버튼에 알린다 — 줄어드는 선은 눈으로만 보이고 초점을 받지 않는다(impeccable 점검 P2).
                val secondsLeft by remember(timeoutKey) {
                    derivedStateOf { ((1f - elapsedFraction) * ApprovalCoordinator.APPROVAL_TIMEOUT_MS / 1000).toInt() }
                }
                KosmosButton(
                    content.rejectLabel, onReject,
                    Modifier.weight(1f).then(if (timeoutKey != null) Modifier.semantics { stateDescription = "${secondsLeft}초 뒤 자동 취소" } else Modifier)
                )
                KosmosButton(content.approveLabel, onApprove, Modifier.weight(1f), accent = true, enabled = approveEnabled)
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
 * 자동 추출 프로필 제안 시트 — 여러 건이면 체크 목록(기본 전부 고름)으로 골라 저장하고, 고르지 않은 항목은 무시한다(0.38.6 M2 사용자 결정).
 * 끌어내리면 [onLater](이번 화면에서만 접고 제안은 남긴다). 무시는 버튼으로만.
 *
 * @param onSave 저장할 제안 id 들 — 나머지는 무시로 처리한다.
 */
@Composable
fun ProfileSuggestionSheet(
    suggestions: List<ProfileSuggestion>,
    onSave: (Set<String>) -> Unit,
    onRejectAll: () -> Unit,
    onLater: () -> Unit
) {
    val colors = KosmosTheme.colors
    val ids = suggestions.map { it.id }
    var selected by remember(ids) { mutableStateOf(ids.toSet()) }
    val base = ApprovalSheetContent.of(suggestions)
    val multi = suggestions.size > 1
    val content = if (multi) base.copy(approveLabel = "고른 ${selected.size}개 저장") else base
    ApprovalSheetLayout(
        content, onApprove = { onSave(selected) }, onReject = onRejectAll, onDismiss = onLater,
        approveEnabled = selected.isNotEmpty(),
        body = if (!multi) null else {
            {
                Column(Modifier.fillMaxWidth().background(colors.surfaceSunk, KosmosShapes.md).padding(vertical = 4.dp)) {
                    suggestions.forEach { s ->
                        val checked = s.id in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(value = checked, role = Role.Checkbox) { selected = if (checked) selected - s.id else selected + s.id }
                                .padding(end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp), colors = CheckboxDefaults.colors(checkedColor = colors.brand))
                            Column(Modifier.weight(1f)) {
                                Text(s.value, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                Text(s.key, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    )
}
