package com.kosmos.app.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 승인·수정 시트의 머리 — 아이콘 · 종류 줄 · 제목 · 보조 줄 · (오른쪽) 큰 시각.
 *
 * [WHY] 제목이 한 줄에 안 들어가면 큰 시각을 제목 아래 줄로 내린다 — 승인 시트에만 있었고 알림 수정 시트는 긴 제목이 낱말 중간에서 끊겼다(0.38.3 점검 P2).
 * 두 시트가 같은 머리를 쓰게 공통 부품으로 뽑았다(0.38.4 M1). 글자 배율로 판정하면 보통 크기의 긴 제목을 놓친다(0.38.3 M0).
 */
@Composable
fun SheetHeader(
    @DrawableRes icon: Int,
    label: String,
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    value: String? = null,
    valueSub: String? = null,
    accentColor: Color = KosmosTheme.colors.brandText
) {
    val colors = KosmosTheme.colors
    var stacked by remember(title, value) { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KosmosIcon(icon, contentDescription = null, size = IconSize.large, tint = accentColor)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = accentColor)
            Text(
                title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary,
                maxLines = 6, overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!stacked && value != null && it.lineCount > 1) stacked = true }
            )
            sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
            if (stacked) value?.let {
                Text(it, style = MaterialTheme.typography.displaySmall, color = colors.textPrimary, modifier = Modifier.padding(top = 4.dp))
                valueSub?.let { s -> Text(s, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
            }
        }
        if (!stacked) value?.let {
            // [WHY] 끝 시각은 큰 시각 아래에 — 왼쪽 날짜 줄에 붙이면 다른 날 일정에서 줄이 넘쳤다.
            Column(horizontalAlignment = Alignment.End) {
                Text(it, style = MaterialTheme.typography.displaySmall, color = colors.textPrimary)
                valueSub?.let { s -> Text(s, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
            }
        }
    }
}
