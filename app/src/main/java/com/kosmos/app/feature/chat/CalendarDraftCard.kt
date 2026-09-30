package com.kosmos.app.feature.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosmos.app.domain.model.CalendarDraft
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.ui.component.ApprovalCardScaffold
import com.kosmos.app.ui.theme.KosmosTheme

// [WHY] 표기는 IsoDateTimeParser 가 단일 출처다(AGENTS §4-7). 예전에는 이 파일이 사설 파서 3개로
// "2026-09-30" / "10:00" 을 만들어 ISO 원문을 그대로 띄웠다. 파싱 실패 시에만 원문 폴백 —
// 승인 카드는 사용자가 무엇을 승인하는지 확인하는 자리라 빈칸보다 원문이 낫다.
private fun formatDraftDate(iso: String): String =
    IsoDateTimeParser.toDisplayDateKorean(iso) ?: iso

private fun formatDraftTime(iso: String): String =
    IsoDateTimeParser.toDisplayTimeKorean(iso) ?: iso

/**
 * [CalendarDraftCard]
 * 모델이 제안한 일정 초안의 승인 카드 — 뼈대는 [ApprovalCardScaffold] 가 공유한다.
 *
 * [WHY] 버튼 문구("거절"/"승인하고 저장")는 E2E 가 버튼을 찾는 셀렉터라 그대로 둔다.
 */
@Composable
fun CalendarDraftCard(
    draft: CalendarDraft,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    ApprovalCardScaffold(
        headerText = "일정을 추가할까요?",
        rejectLabel = "거절",
        approveLabel = "승인하고 저장",
        onReject = onReject,
        onApprove = onApprove
    ) {
        Text(
            text = draft.title,
            color = KosmosTheme.colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        DraftLine(icon = "📅", text = formatDraftDate(draft.startIso))
        Spacer(modifier = Modifier.height(8.dp))
        DraftLine(
            icon = "🕒",
            text = buildString {
                append(formatDraftTime(draft.startIso))
                draft.endIso?.let { append(" - ").append(formatDraftTime(it)) }
            }
        )
        draft.note?.let { note ->
            Spacer(modifier = Modifier.height(8.dp))
            DraftLine(icon = "💬", text = note, muted = true)
        }
    }
}

@Composable
private fun DraftLine(icon: String, text: String, muted: Boolean = false) {
    Row(verticalAlignment = if (muted) Alignment.Top else Alignment.CenterVertically) {
        Text(icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = if (muted) KosmosTheme.colors.textMuted else KosmosTheme.colors.textSecondary,
            style = if (muted) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
        )
    }
}
