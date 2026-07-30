package com.kosmos.app.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.kosmos.app.ui.theme.Cyan
import com.kosmos.app.ui.theme.Violet
import kotlin.math.sin

@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    // [WHY] 백그라운드/정지 상태에서도 무한 애니메이션이 매 프레임 전체 화면 Canvas를 다시 그리면
    // 온디바이스 LLM 앱에 상시 GPU/배터리 비용이 얹힌다. RESUMED 상태에서만 transition을 구성한다.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val animateState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            animateState.value = event.targetState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val time: Float
    if (animateState.value) {
        val infiniteTransition = rememberInfiniteTransition(label = "Aurora")
        val animatedTime by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "time"
        )
        time = animatedTime
    } else {
        time = 0f
    }

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val x1 = width * 0.3f + width * 0.2f * sin(time)
            val y1 = height * 0.2f + height * 0.1f * sin(time * 1.5f)
            
            val x2 = width * 0.8f + width * 0.15f * sin(time + 2f)
            val y2 = height * 0.8f + height * 0.15f * sin(time * 0.8f)

            val x3 = width * 0.6f + width * 0.2f * sin(time * 1.2f + 1f)
            val y3 = height * 0.5f + height * 0.2f * sin(time * 0.9f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Cyan.copy(alpha = 0.18f), Cyan.copy(alpha = 0f)),
                    center = Offset(x1, y1),
                    radius = width * 0.7f
                ),
                center = Offset(x1, y1),
                radius = width * 0.7f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Violet.copy(alpha = 0.15f), Violet.copy(alpha = 0f)),
                    center = Offset(x2, y2),
                    radius = width * 0.6f
                ),
                center = Offset(x2, y2),
                radius = width * 0.6f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Cyan.copy(alpha = 0.1f), Cyan.copy(alpha = 0f)),
                    center = Offset(x3, y3),
                    radius = width * 0.5f
                ),
                center = Offset(x3, y3),
                radius = width * 0.5f
            )
        }
        content()
    }
}
