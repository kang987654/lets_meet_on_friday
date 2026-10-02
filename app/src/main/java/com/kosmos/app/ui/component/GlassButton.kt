package com.kosmos.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 글래스 버튼 — 화면마다 복사돼 있던 "글래스 상자 + 클릭 + 가운데 글자"를 하나로 모은 것.
 *
 * @param accent 주요 동작(강조 테두리·굵은 강조색 글자). false 면 보조 동작.
 * @param enabled false 면 누를 수 없고 글자가 흐려진다.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true
) {
    val colors = KosmosTheme.colors
    Box(
        modifier = modifier
            .glassEffect(
                backgroundColor = if (accent) colors.accent.copy(alpha = 0.2f) else null,
                borderColor = if (accent) colors.accent.copy(alpha = 0.5f) else null,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> colors.textMuted
                accent -> colors.accent
                else -> colors.textPrimary
            },
            fontWeight = if (accent) FontWeight.Bold else null
        )
    }
}
