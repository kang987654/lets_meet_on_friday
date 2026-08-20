package com.kosmos.app.feature.chat

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.kosmos.app.ui.component.glassEffect
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 아침 브리핑 카드 (시안 A′-1: "브리핑이 비서 카드로 대화에 도착").
 *
 * ### Architecture Context
 * - **Layer**: Feature (Chat)
 * - **Dependencies**: 없음 (표시 전용)
 *
 * [WHY] ChatBubbleAssistant 를 확장하지 않고 별도 컴포저블이다 — 기존 버블은 E2E 계약
 * 표면이라 건드리지 않고, 브리핑은 말풍선이 아니라 "도착한 카드"라는 다른 시각 문법을 쓴다.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BriefingCard(
    text: String,
    onLongPress: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(
                backgroundColor = KosmosTheme.colors.glassMid,
                borderColor = KosmosTheme.colors.accent.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp)
            )
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "☀️", fontSize = 14.sp)
            Text(
                text = "아침 브리핑",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = KosmosTheme.colors.accent
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides KosmosTheme.colors.textPrimary
        ) {
            ProvideTextStyle(
                value = MaterialTheme.typography.bodyMedium.copy(color = KosmosTheme.colors.textPrimary)
            ) {
                RichText {
                    Markdown(content = text)
                }
            }
        }
    }
}
