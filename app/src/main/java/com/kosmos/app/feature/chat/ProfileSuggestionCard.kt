package com.kosmos.app.feature.chat

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kosmos.app.domain.model.ProfileSuggestion
import com.kosmos.app.ui.component.ApprovalCardScaffold
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * [ProfileSuggestionCard]
 * 자동 추출된 프로필 급 사실의 승인 카드 — 입력바 위에 떠서 [저장]/[무시]로 종결합니다 (C′2).
 *
 * [WHY] CalendarDraftCard 와 같은 문법(플로팅 글래스 카드, 승인/거절 두 버튼) — 앱에서 "승인"은
 * 오버레이다. 타임라인 카드(BriefingCard 방식)는 카드 상태를 메시지에 표현해야 하고 본문이 모델
 * 히스토리에 섞이는 문제가 있어 기각(사용자 결정 2026-09-02). 한 번에 한 건만 보이고 나머지는
 * "외 N건" — 제안은 테이블에 영속이라 놓칠 일이 없다. 뼈대는 [ApprovalCardScaffold] 가 공유한다.
 */
@Composable
fun ProfileSuggestionCard(
    suggestion: ProfileSuggestion,
    remaining: Int,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    ApprovalCardScaffold(
        headerText = "기억해 둘까요?",
        rejectLabel = "무시",
        approveLabel = "프로필에 저장",
        onReject = onReject,
        onApprove = onAccept,
        headerTrailing = {
            if (remaining > 0) {
                Text(
                    text = "외 ${remaining}건",
                    color = KosmosTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    ) {
        Text(
            text = suggestion.key,
            color = KosmosTheme.colors.textMuted,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = suggestion.value,
            color = KosmosTheme.colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "대화에서 찾은 항목이에요. 저장하면 비서가 항상 기억해요.",
            color = KosmosTheme.colors.textMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
