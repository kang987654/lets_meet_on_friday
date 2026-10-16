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
 * 앱의 표면 — 불투명 면 + hairline 테두리(DESIGN.md v3 Elevation). 그림자·블러·반투명을 쓰지 않는다.
 *
 * [WHY] 기본 색이 테마에 따라 달라져야 하므로 파라미터 기본값을 상수로 둘 수 없다.
 * null로 받아 `composed {}`(컴포지션 스코프) 안에서 [KosmosTheme] 토큰으로 해석한다.
 */
fun Modifier.kosmosSurface(
    shape: Shape,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp
): Modifier = composed {
    val resolvedBackground = backgroundColor ?: KosmosTheme.colors.surface
    val resolvedBorder = borderColor ?: KosmosTheme.colors.border
    this.then(
        Modifier
            .background(resolvedBackground, shape)
            .border(borderWidth, resolvedBorder, shape)
    )
}
