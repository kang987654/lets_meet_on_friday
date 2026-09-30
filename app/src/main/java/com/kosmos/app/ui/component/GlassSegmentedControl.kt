package com.kosmos.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * [GlassSegmentedControl]
 * 글래스 트랙 위에서 하나를 고르는 세그먼트 컨트롤 — 선택 칸만 accent 틴트·굵은 글씨.
 *
 * ### Architecture Context
 * - **Layer**: UI (Component)
 * - **Dependencies**: [KosmosTheme], [glassEffect]
 *
 * [WHY] 설정의 테마·응답 스타일, 일정의 오늘/이번 주가 같은 컨트롤을 세 벌 복제하고 있었다.
 * 모서리·세로 여백만 화면마다 달라 인자로 받는다(값은 기존 그대로라 모양이 바뀌지 않는다).
 */
@Composable
fun <T> GlassSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    verticalPadding: Dp = 12.dp,
    textStyle: TextStyle = LocalTextStyle.current
) {
    val shape = RoundedCornerShape(cornerRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassEffect(shape = shape),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) KosmosTheme.colors.accent.copy(alpha = 0.2f) else Color.Transparent,
                        shape = shape
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = verticalPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) KosmosTheme.colors.accent else KosmosTheme.colors.textMuted,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    style = textStyle
                )
            }
        }
    }
}
