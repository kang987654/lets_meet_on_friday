package com.kosmos.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * [ApprovalCardScaffold]
 * 입력바 위에 뜨는 승인 카드의 공통 뼈대 — "K" 배지 헤더, 내용 글래스 박스, 거절/승인 버튼 줄.
 *
 * ### Architecture Context
 * - **Layer**: UI (Component)
 * - **Dependencies**: [KosmosTheme], [glassEffect]
 *
 * ### Key Flow
 * 1. [headerText] 와 선택적 [headerTrailing](예: "외 N건")으로 헤더를 그린다.
 * 2. [body] 를 내부 글래스 박스에 담는다.
 * 3. [rejectLabel]/[approveLabel] 두 버튼으로 종결한다.
 *
 * [WHY] 일정 초안 카드와 프로필 제안 카드가 거의 한 줄 한 줄 같은 코드였다 — "앱에서 승인은
 * 오버레이"라는 한 문법(ProfileSuggestionCard KDoc)이면 뼈대도 하나여야 한쪽만 바뀌지 않는다.
 * 버튼 문구는 호출자가 준다 — E2E 가 그 문구로 버튼을 찾는다.
 */
@Composable
fun ApprovalCardScaffold(
    headerText: String,
    rejectLabel: String,
    approveLabel: String,
    onReject: () -> Unit,
    onApprove: () -> Unit,
    headerTrailing: @Composable RowScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit
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
                text = headerText,
                color = KosmosTheme.colors.textMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.weight(1f))
            headerTrailing()
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
            Column(content = body)
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
                Text(rejectLabel, color = KosmosTheme.colors.danger, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onApprove() }
                    .background(
                        brush = Brush.linearGradient(
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
                Text(approveLabel, color = KosmosTheme.colors.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
