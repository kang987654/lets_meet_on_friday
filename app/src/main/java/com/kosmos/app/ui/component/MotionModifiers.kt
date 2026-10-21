package com.kosmos.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kosmos.app.ui.theme.Motion

/**
 * 누르는 동안 0.97배로 살짝 줄어든다(0.38.4 M4, emil "버튼 누름 피드백"). "애니메이션 제거"면 그대로.
 *
 * [source] 는 같은 요소의 `clickable(interactionSource = …)` 와 공유해야 눌림을 안다.
 */
@Composable
fun Modifier.pressScale(source: InteractionSource): Modifier {
    val reduced = rememberReducedMotion()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !reduced) 0.97f else 1f, Motion.short(), label = "pressScale")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * 처음 그려질 때 한 번만 아래 8dp 에서 올라오며 나타난다(0.38.4 M4 결과 카드). [enabled] 가 false 면(이미 있던 것·애니메이션 제거) 바로 보인다.
 *
 * [WHY] "결과가 생길 때만 작게 움직인다"(DESIGN Motion) — 스크롤로 다시 보일 때마다 움직이면 안 되므로 호출자가 새로 생긴 것만 켠다.
 */
@Composable
fun Modifier.appearOnce(enabled: Boolean): Modifier {
    val reduced = rememberReducedMotion()
    val animate = enabled && !reduced
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    if (animate) LaunchedEffect(Unit) { progress.animateTo(1f, Motion.medium()) }
    val shift = with(LocalDensity.current) { 8.dp.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * shift
    }
}
