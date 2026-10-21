package com.kosmos.app.feature.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kosmos.app.ui.component.sparklePath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * 은하 장면(0.38.4 M6 2안) — 비스듬한 나선 은하 + 팔 위의 우리 별 → 별 자리로 빛줄기 → 별 자리에서 채팅이 번지며 줌인 → 별이 헤더 별 자리로 날아가 앉음.
 *
 * 시각은 ms. 전환 시각 `te` 는 전환 시작부터 잰다. 크기는 화면 비율로 잡는다.
 */
internal object GalaxyScene {
    const val ASSEMBLE_MS = 1400L
    private const val WARP_MS = 1100f
    /** 별이 나타나는 장면 시각. */
    const val STAR_POP_MS = 1200L
    /** 채팅이 번지기 시작해 다 덮기까지. */
    private const val OPEN_FROM_MS = 150f
    private const val OPEN_MS = 700f
    /** 다 덮은 뒤 1.03 에서 1.0 으로 내려앉는 시간. */
    private const val SETTLE_FROM_MS = 850f
    private const val SETTLE_MS = 220f
    /** 별이 헤더로 날아가는 구간. */
    private const val FLY_FROM_MS = 760f
    private const val FLY_MS = 440f
    const val END_MS = 1200L

    /** 0..1 반지름, 각, 밝기. */
    internal class G(val r: Float, val th: Float, val bright: Float)

    /** 팔 2개에 1,200점 + 중심부 380점. */
    val points: List<G> = run {
        val rnd = Random(21)
        fun gauss() = rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f
        val list = ArrayList<G>(1580)
        repeat(1200) {
            val arm = it % 2
            val t = 0.12f + 0.88f * rnd.nextFloat().pow(0.8f)
            val spread = gauss() * 0.42f * (0.35f + t)
            list += G(t * (1f + gauss() * 0.06f), arm * PI.toFloat() + t * 3.6f + spread, 0.3f + 0.45f * (1 - t) + rnd.nextFloat() * 0.25f)
        }
        repeat(380) { list += G(abs(gauss()) * 0.16f, rnd.nextFloat() * 2 * PI.toFloat(), 0.7f + rnd.nextFloat() * 0.3f) }
        list
    }

    /** 우리 별 — 두 번째 팔 중간(팔 각도 공식 arm·π + r·3.6 과 같은 자리). */
    private const val STAR_R = 0.5f
    private val STAR_TH = PI.toFloat() + STAR_R * 3.6f

    /** 안쪽일수록 빨리 돈다(rad/s). */
    private fun omega(r: Float) = 0.35f / (0.25f + r)

    fun center(size: Size) = Offset(size.width / 2, size.height * 0.36f)
    private fun radius(size: Size) = size.width * 0.46f

    /** 은하 좌표 → 화면. 두 번째 값은 앞(+)/뒤(-). */
    private fun gpos(r: Float, th: Float, tSec: Float, c: Offset, rg: Float): Pair<Offset, Float> {
        val a = th + omega(r) * tSec
        val x = cos(a) * r * rg
        val y0 = sin(a) * r * rg * 0.42f
        val tilt = -0.35f
        return Offset(c.x + x * cos(tilt) - y0 * sin(tilt), c.y + x * sin(tilt) + y0 * cos(tilt)) to sin(a)
    }

    /** 우리 별 자리 — 전환이 시작되면 그 자리에 머문다. */
    fun star(size: Size, tMs: Long, exitAt: Long): Offset =
        gpos(STAR_R, STAR_TH, min(tMs, exitAt) / 1000f, center(size), radius(size)).first

    fun warp(te: Float) = clamp01(te / WARP_MS)
    fun open(te: Float) = easeIn(clamp01((te - OPEN_FROM_MS) / OPEN_MS))

    /** 채팅 배율 — 0.55 에서 1.03 까지 다가왔다가 1.0 으로 내려앉는다. */
    fun chatScale(te: Float): Float {
        val o = open(te)
        return if (o < 1f) 0.55f + 0.48f * easeOut(o) else 1.03f - 0.03f * easeOut(clamp01((te - SETTLE_FROM_MS) / SETTLE_MS))
    }

    /** 드러난 채팅의 반지름 — 안쪽 55% 는 또렷하고 바깥으로 흐려진다. */
    fun revealRadius(size: Size, vp: Offset, te: Float): Float {
        val far = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height)).maxOf { (it - vp).getDistance() }
        return (far / 0.55f) * open(te) + 1f
    }

    /** 별이 커졌다가(돌진) 휜 곡선으로 헤더 별 자리에 앉는다. 반환: 위치, 반지름, 빛무리 세기. */
    fun flyingStar(vp: Offset, target: Offset, targetRadius: Float, te: Float, density: Float): Triple<Offset, Float, Float> {
        val fly = easeInOut(clamp01((te - FLY_FROM_MS) / FLY_MS))
        val grow = easeOut(clamp01(te / 600f))
        val r = (6f + 8f * grow) * density * (1f - fly) + targetRadius * fly
        val mid = Offset((vp.x + target.x) / 2 + 40f * density, (vp.y + target.y) / 2)
        val u = fly
        val pos = vp * ((1 - u) * (1 - u)) + mid * (2 * u * (1 - u)) + target * (u * u)
        return Triple(pos, r, 1f - 0.7f * fly)
    }
}

/** 은하와 배경 별, 전환 전에는 우리 별까지 그린다. */
internal class GalaxyPainter {
    private val starDots = DotBatch(lines = false)
    private val starLines = DotBatch(lines = true)
    private val armDots = DotBatch(lines = false)
    private val coreDots = DotBatch(lines = false)
    private val armLines = DotBatch(lines = true)
    private val coreLines = DotBatch(lines = true)

    fun draw(scope: DrawScope, tMs: Long, exitAt: Long, color: Color, core: Color) = with(scope) {
        val c = GalaxyScene.center(size)
        val rg = size.width * 0.46f
        val tSec = min(tMs, exitAt) / 1000f + max(0L, tMs - exitAt) / 4000f
        val assemble = easeOut(clamp01(tMs / GalaxyScene.ASSEMBLE_MS.toFloat()))
        val e = if (tMs <= exitAt) 0f else GalaxyScene.warp((tMs - exitAt).toFloat())
        val vp = GalaxyScene.star(size, tMs, exitAt)
        val w = easeIn(clamp01(e / 0.8f))
        val z = 1f - 0.93f * w
        val trail = 0.02f + 0.25f * w
        val fade = 1f - clamp01((e - 0.5f) / 0.45f)
        fun project(p: Offset, zz: Float) = Offset(vp.x + (p.x - vp.x) / zz, vp.y + (p.y - vp.y) / zz)
        val stroke = 1.1f * density * (1f + 0.6f * w)

        drawCircle(Brush.radialGradient(listOf(core.copy(alpha = 0.32f * assemble * fade), Color.Transparent), c, rg * 0.45f), rg * 0.45f, c)
        val starAlpha = (0.25f + 0.4f * w) * assemble * fade
        BackgroundStars.points.forEach { s ->
            val base = Offset(s.x * size.width, s.y * size.height)
            if (e <= 0f) starDots.point(starAlpha, base) else starLines.line(starAlpha, project(base, z + trail), project(base, z))
        }
        starDots.flush(this, color, 2f * density)
        starLines.flush(this, color, stroke * 0.6f)

        GalaxyScene.points.forEach { g ->
            val a = g.th + 0.35f / (0.25f + g.r * (0.55f + 0.45f * assemble)) * tSec
            val rr = g.r * (0.55f + 0.45f * assemble) * rg
            val x = cos(a) * rr
            val y0 = sin(a) * rr * 0.42f
            val pos = Offset(c.x + x * COS_TILT - y0 * SIN_TILT, c.y + x * SIN_TILT + y0 * COS_TILT)
            val alpha = g.bright * (if (sin(a) < 0) 0.7f else 1f) * assemble * fade * (1f - 0.5f * w)
            val isCore = g.r < 0.12f
            if (e <= 0f) (if (isCore) coreDots else armDots).point(alpha, pos)
            else (if (isCore) coreLines else armLines).line(alpha, project(pos, z + trail), project(pos, z))
        }
        armDots.flush(this, color, 1.8f * density)
        coreDots.flush(this, core, 1.8f * density)
        armLines.flush(this, color, stroke)
        coreLines.flush(this, core, stroke)

        if (e <= 0f) {
            val pop = easeOut(clamp01((tMs - GalaxyScene.STAR_POP_MS) / 500f))
            val r = 6f * density * pop * (1f + 0.15f * sin(tMs / 260f))
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f * pop), Color.Transparent), vp, r * 3.2f + 1f), r * 3.2f + 1f, vp)
            if (r > 0f) drawPath(sparklePath(vp, r), color.copy(alpha = pop))
        }
    }

    private companion object {
        val COS_TILT = cos(-0.35f)
        val SIN_TILT = sin(-0.35f)
    }
}
