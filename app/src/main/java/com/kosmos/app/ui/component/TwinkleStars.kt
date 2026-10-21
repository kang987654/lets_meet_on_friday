package com.kosmos.app.ui.component

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kosmos.app.ui.theme.KosmosTheme
import kotlin.math.PI
import kotlin.math.cos

/**
 * 응답 대기 표시 — 네 갈래 별 셋이 차례로 반짝인다(0.38.2 M7, 사용자 선택 "우주 컨셉" B).
 *
 * DESIGN.md "개성" 자리. 응답을 기다리는 동안에만 돈다(장식용 무한 애니메이션 금지 — Motion).
 * 시스템 "애니메이션 제거"가 켜져 있으면 가운데 별만 밝은 멈춘 그림.
 */
@Composable
fun TwinkleStars(modifier: Modifier = Modifier) {
    val color = KosmosTheme.colors.brand
    val reduceMotion = rememberReducedMotion()
    val t by if (reduceMotion) remember { androidx.compose.runtime.mutableFloatStateOf(1f / 3) } else {
        rememberInfiniteTransition(label = "twinkle").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PERIOD_MS, easing = LinearEasing)),
            label = "phase"
        )
    }
    Canvas(
        modifier
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .size(width = 44.dp, height = 20.dp)
            .semantics { contentDescription = "답을 준비하는 중" }
    ) {
        PHASES.forEachIndexed { i, phase ->
            val p = (t - phase + 1f) % 1f
            val k = (0.5 - 0.5 * cos(2 * PI * p)).toFloat()
            val base = if (i == 1) 8.dp.toPx() else 6.dp.toPx()
            sparkle(Offset(size.width * (0.18f + 0.32f * i), size.height / 2), base * (0.55f + 0.45f * k), color.copy(alpha = 0.35f + 0.65f * k))
        }
    }
}

/**
 * 네 갈래 별 하나 — 헤더의 엔진 상태 표시(색이 상태)와 빈 채팅 별자리가 같은 모양을 쓴다(DESIGN.md "개성" ③).
 */
@Composable
fun StarGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { sparkle(center, size.minDimension / 2, color) }
}

private const val PERIOD_MS = 1500
private val PHASES = listOf(0f, 1f / 3, 2f / 3)

/** 네 갈래 별 — 꼭짓점 넷을 안으로 휜 곡선으로 잇는다. */
internal fun DrawScope.sparkle(o: Offset, r: Float, color: Color) {
    val w = r * 0.11f
    val path = Path().apply {
        moveTo(o.x, o.y - r)
        quadraticTo(o.x + w, o.y - w, o.x + r, o.y)
        quadraticTo(o.x + w, o.y + w, o.x, o.y + r)
        quadraticTo(o.x - w, o.y + w, o.x - r, o.y)
        quadraticTo(o.x - w, o.y - w, o.x, o.y - r)
        close()
    }
    drawPath(path, color)
}
