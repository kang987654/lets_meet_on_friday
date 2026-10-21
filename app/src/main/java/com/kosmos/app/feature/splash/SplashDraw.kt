package com.kosmos.app.feature.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow

internal fun easeOut(x: Float) = 1f - (1f - x).pow(3)
internal fun easeIn(x: Float) = x * x
internal fun easeInOut(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
internal fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/**
 * 같은 색·굵기의 점이나 선을 투명도 단계별로 모아 한 번에 그린다.
 *
 * [WHY] 장면 하나가 프레임마다 점 1,000~2,000개를 그린다 — 점마다 drawCircle 을 부르면 GPU 에 모델을 올리는 동안(엔진 준비)
 * 프레임이 밀린다. 투명도를 [LEVELS] 단계로 묶어 단계마다 drawPoints/drawLines 한 번으로 줄인다. 배열은 다시 쓴다.
 */
internal class DotBatch(private val lines: Boolean) {
    private val buf = Array(LEVELS) { FloatArray(256) }
    private val count = IntArray(LEVELS)
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    fun point(alpha: Float, p: Offset) = add(alpha, p.x, p.y, 0f, 0f)

    fun line(alpha: Float, a: Offset, b: Offset) = add(alpha, a.x, a.y, b.x, b.y)

    private fun add(alpha: Float, x0: Float, y0: Float, x1: Float, y1: Float) {
        if (alpha <= 0.004f) return
        val level = (clamp01(alpha) * LEVELS).toInt().coerceIn(0, LEVELS - 1)
        val step = if (lines) 4 else 2
        var arr = buf[level]
        val n = count[level]
        if (n + step > arr.size) {
            arr = arr.copyOf(arr.size * 2)
            buf[level] = arr
        }
        arr[n] = x0; arr[n + 1] = y0
        if (lines) { arr[n + 2] = x1; arr[n + 3] = y1 }
        count[level] = n + step
    }

    /** 모은 것을 [color] 로 그리고 비운다. [width] 는 점의 지름 또는 선 굵기(px). */
    fun flush(scope: DrawScope, color: Color, width: Float) {
        paint.strokeWidth = width
        scope.drawIntoCanvas { canvas ->
            for (level in 0 until LEVELS) {
                val n = count[level]
                if (n == 0) continue
                paint.color = color.copy(alpha = color.alpha * (level + 0.5f) / LEVELS).toArgb()
                if (lines) canvas.nativeCanvas.drawLines(buf[level], 0, n, paint)
                else canvas.nativeCanvas.drawPoints(buf[level], 0, n, paint)
                count[level] = 0
            }
        }
    }

    private companion object {
        const val LEVELS = 12
    }
}
