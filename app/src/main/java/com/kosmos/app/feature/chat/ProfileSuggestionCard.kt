package com.kosmos.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosmos.app.domain.model.ProfileSuggestion
import com.kosmos.app.ui.component.glassEffect
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * [ProfileSuggestionCard]
 * 자동 추출된 프로필 급 사실의 승인 카드 — 입력바 위에 떠서 [저장]/[무시]로 종결합니다 (C′2).
 *
 * [WHY] CalendarDraftCard 와 같은 문법(플로팅 글래스 카드, 승인/거절 두 버튼) — 앱에서 "승인"은
 * 오버레이다. 타임라인 카드(BriefingCard 방식)는 카드 상태를 메시지에 표현해야 하고 본문이 모델
 * 히스토리에 섞이는 문제가 있어 기각(사용자 결정 2026-09-02). 한 번에 한 건만 보이고 나머지는
 * "외 N건" — 제안은 테이블에 영속이라 놓칠 일이 없다.
 */
@Composable
fun ProfileSuggestionCard(
    suggestion: ProfileSuggestion,
    remaining: Int,
    onAccept: () -> Unit,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(KosmosTheme.colors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("K", color = KosmosTheme.colors.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "기억해 둘까요?",
                color = KosmosTheme.colors.textMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.weight(1f))
            if (remaining > 0) {
                Text(
                    text = "외 ${remaining}건",
                    color = KosmosTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                Text("무시", color = KosmosTheme.colors.danger, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onAccept() }
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                KosmosTheme.colors.accent.copy(alpha = 0.2f),
                                KosmosTheme.colors.accentAlt.copy(alpha = 0.2f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, KosmosTheme.colors.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("프로필에 저장", color = KosmosTheme.colors.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
