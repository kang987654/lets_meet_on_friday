package com.kosmos.app.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 앱의 유일한 아이콘 그리기 — Phosphor(MIT) VectorDrawable(`res/drawable/ic_ph_*`)만 받는다(DESIGN.md v3 Icons).
 *
 * 크기는 [IconSize] 단계, 색은 토큰. 눌리는 아이콘은 `IconButton` 안에 두고 [contentDescription] 을 준다 —
 * 장식용(옆에 같은 뜻의 글자가 있음)이면 null.
 */
@Composable
fun KosmosIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = IconSize.default,
    tint: Color = KosmosTheme.colors.textPrimary,
) {
    Icon(
        painter = painterResource(id = icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/** 아이콘 크기 단계 — 글자 옆 20, 기본 24, 빈 화면 32(DESIGN.md v3). */
object IconSize {
    val inline = 20.dp
    val default = 24.dp
    val large = 32.dp
}
