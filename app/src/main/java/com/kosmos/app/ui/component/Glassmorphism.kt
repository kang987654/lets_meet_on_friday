package com.kosmos.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * Figma의 backdrop-filter: blur(20px) 효과를 Android(Compose)로 이식하는 Modifier.
 * Shape 및 테두리를 함께 적용하며, 색상은 현재 테마(라이트/다크)의 Glass 토큰을 따릅니다.
 *
 * [WHY] 기본 색상이 테마에 따라 달라져야 하므로 파라미터 기본값을 상수로 둘 수 없다.
 * null로 받아 `composed {}`(컴포지션 스코프) 안에서 [KosmosTheme] 토큰으로 해석한다.
 */
fun Modifier.glassEffect(
    shape: Shape,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp
): Modifier = composed {
    val resolvedBackground = backgroundColor ?: KosmosTheme.colors.glass
    val resolvedBorder = borderColor ?: KosmosTheme.colors.border
    this.then(
        Modifier
            .background(resolvedBackground, shape)
            .border(borderWidth, resolvedBorder, shape)
    )
}
