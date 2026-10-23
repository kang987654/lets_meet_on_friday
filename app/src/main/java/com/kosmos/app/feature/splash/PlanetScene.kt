package com.kosmos.app.feature.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.kosmos.app.ui.component.sparklePath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 행성 장면(0.38.4 M6 1안) — 입자 행성 → 워프 → 빛이 채팅 요소 자리에 박힘 → 모자이크로 채팅이 켜짐 → 우리 별 혜성이 헤더 별에 앉음.
 *
 * 시각은 모두 ms, 전환 진행 `e` 는 전환 시작부터 [WARP_MS] 를 0..1 로 잰 값이다. 크기는 화면 비율로 잡는다(시안은 393×600dp).
 */
internal object PlanetScene {
    const val ASSEMBLE_MS = 1200L
    const val WARP_MS = 900L
    private const val ROTATION_MS = 4000f
    private const val BUILDERS = 170
    /** 혜성이 헤더 별에 닿는 워프 진행. */
    const val COMET_LAND = 0.85f
    const val PULSE_MS = 450L
    const val TILE_DP = 10f
    /** 전환 시작부터 덮개를 걷기까지. */
    val END_MS: Long = max(WARP_MS, (COMET_LAND * WARP_MS).toLong() + PULSE_MS)

    /** 입력바로 가는 빛의 몫 — 넓은 테두리라 같은 비율이면 대부분이 아래로 몰렸다(시안 사용자 피드백). */
    private const val INPUT_BAR_WEIGHT = 0.4f

    /** [kind] 0 구체(a=극각, b=방위각) / 1 고리(a=각, b=궤도 반지름 배율). [sx]·[sy] 는 모이기 전 자리, [sp] 는 흩어지는 세기. */
    internal class P(val sx: Float, val sy: Float, val kind: Int, val a: Float, val b: Float, val sp: Float)

    /** 구체 420(피보나치 배치) + 기울어진 고리 380. 처음 자리는 화면 여기저기. */
    val particles: List<P> = run {
        val rnd = Random(7)
        val sphere = 420
        val list = ArrayList<P>(800)
        // [WHY] 난수를 뽑는 순서까지 승인한 시안과 같게 둔다 — 시안에서 본 장면이 그대로 나오도록.
        for (i in 0 until sphere) {
            val y = 1f - 2f * (i + 0.5f) / sphere
            val phi = acos(y)
            val theta = (PI * (3 - sqrt(5.0)) * i).toFloat()
            rnd.nextFloat()
            list += P(rnd.nextFloat() * 2 - 1, rnd.nextFloat() * 2 - 1, 0, phi, theta, 0.5f + rnd.nextFloat()).also { rnd.nextFloat() }
        }
        repeat(380) {
            rnd.nextFloat()
            val sx = rnd.nextFloat() * 2 - 1
            val sy = rnd.nextFloat() * 2 - 1
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val r = 1.45f + rnd.nextFloat() * 0.5f
            list += P(sx, sy, 1, a, r, 0.5f + rnd.nextFloat()).also { rnd.nextFloat() }
        }
        list
    }

    fun center(size: Size) = Offset(size.width / 2, size.height * 0.36f)
    fun radius(size: Size) = size.minDimension * 0.17f

    /** 다 모인 뒤 입자 자리와 앞(+)/뒤(-). */
    fun home(p: P, tMs: Long, r: Float, c: Offset): Pair<Offset, Float> {
        val rot = tMs / ROTATION_MS * 2 * PI.toFloat()
        val tilt = -0.32f
        return if (p.kind == 0) {
            val x = sin(p.a) * cos(p.b + rot)
            val z = sin(p.a) * sin(p.b + rot)
            Offset(c.x + x * r, c.y + cos(p.a) * r) to z
        } else {
            val ang = p.a + rot * 0.6f
            val x = cos(ang) * p.b * r
            val y0 = sin(ang) * p.b * r * 0.28f
            Offset(c.x + x * cos(tilt) - y0 * sin(tilt), c.y + x * sin(tilt) + y0 * cos(tilt)) to sin(ang)
        }
    }

    /** 박히는 빛 하나 — 입자 번호, 목표 자리, 처음 반지름, 출발 반지름, 처음 방향, 닿는 워프 진행. */
    internal class Builder(val idx: Int, val target: Offset, val r0: Float, val rStart: Float, val dir0: Offset, val eLand: Float)

    /**
     * 채팅 요소 테두리 위 자리마다 날아가던 방향이 가장 가까운 입자를 고른다(앞면 우선). [targets] 는 (자리, 몫).
     */
    fun builders(size: Size, targets: List<Pair<Rect, Float>>, exitAtMs: Long, density: Float): List<Builder> {
        if (targets.isEmpty()) return emptyList()
        val c = center(size)
        val r = radius(size)
        val per = targets.map { (rect, w) -> 2 * (rect.width + rect.height) * w }
        val total = per.sum().coerceAtLeast(1f)
        val rnd = Random(9)
        val spots = ArrayList<Offset>()
        targets.forEachIndexed { k, (rect, _) ->
            val n = (BUILDERS * per[k] / total).toInt().coerceAtLeast(8)
            repeat(n) { spots += onPerimeter(rect, (it + rnd.nextFloat()) / n, 6f * density) }
        }
        val homes = particles.map { home(it, exitAtMs, r, c) }
        val used = BooleanArray(particles.size)
        return spots.shuffled(rnd).mapNotNull { t ->
            val ta = atan2(t.y - c.y, t.x - c.x)
            var best = -1
            var bestScore = Float.MAX_VALUE
            homes.forEachIndexed { i, (hp, depth) ->
                if (used[i]) return@forEachIndexed
                var d = abs(atan2(hp.y - c.y, hp.x - c.x) - ta)
                if (d > PI) d = (2 * PI).toFloat() - d
                val score = d + (if (depth < 0) 0.4f else 0f)
                if (score < bestScore) { bestScore = score; best = i }
            }
            if (best < 0) return@mapNotNull null
            used[best] = true
            val hp = homes[best].first
            val r0 = (hp - c).getDistance().coerceAtLeast(1f)
            val rT = (t - c).getDistance().coerceAtLeast(1f)
            val rStart = r0.coerceIn(rT * 0.14f, rT * 0.5f)
            val wStar = clamp01((1f - rStart / rT) / 0.9f)
            Builder(best, t, r0, rStart, (hp - c) / r0, 0.7f * sqrt(wStar))
        }
    }

    private fun onPerimeter(r: Rect, u: Float, inset: Float): Offset {
        val w = (r.width - 2 * inset).coerceAtLeast(0f)
        val h = (r.height - 2 * inset).coerceAtLeast(0f)
        var d = u * 2 * (w + h)
        if (d < w) return Offset(r.left + inset + d, r.top); d -= w
        if (d < h) return Offset(r.right, r.top + inset + d); d -= h
        if (d < w) return Offset(r.right - inset - d, r.bottom); d -= w
        return Offset(r.left, r.bottom - inset - d)
    }

    /** 칸마다 켜지기 시작하는 워프 진행 — 가장 가까운 박힌 자리의 시각 + 거리 + 약간의 흔들림. */
    class Tiles(val cols: Int, val rows: Int, val tile: Float, val start: FloatArray) {
        val earliest: Float = start.minOrNull() ?: 0f
    }

    fun tiles(size: Size, list: List<Builder>, density: Float): Tiles {
        val tile = TILE_DP * density
        val cols = ceil(size.width / tile).toInt()
        val rows = ceil(size.height / tile).toInt()
        val rnd = Random(3)
        val start = FloatArray(cols * rows)
        for (row in 0 until rows) for (col in 0 until cols) {
            val cx = (col + 0.5f) * tile
            val cy = (row + 0.5f) * tile
            var best = if (list.isEmpty()) 0.5f else Float.MAX_VALUE
            list.forEach { b ->
                val dx = cx - b.target.x
                val dy = cy - b.target.y
                best = min(best, b.eLand + sqrt(dx * dx + dy * dy) / density / 100f * 0.18f)
            }
            start[row * cols + col] = best + rnd.nextFloat() * 0.06f
        }
        return Tiles(cols, rows, tile, start)
    }

    /** 칸 하나가 꺼짐 → 켜짐을 세 단계로(모자이크). */
    fun tileAlpha(start: Float, e: Float): Float = floor(clamp01((e - start) / 0.08f) * 3f) / 3f

    /** 워프 진행 [e] 에 아직 덮여 있는 칸을 [bg] 로 그린다. */
    fun DrawScope.drawTiles(tiles: Tiles?, e: Float, bg: Color) {
        if (tiles == null || e <= tiles.earliest) { drawRect(bg); return }
        val paths = Array(3) { Path() }
        // [WHY] 같은 단계가 이어지는 칸은 한 줄 사각형으로 묶는다 — 칸마다 넣으면 프레임마다 수천 개라 전환 프레임이 무거웠다(0.38.6 M1).
        for (row in 0 until tiles.rows) {
            var runLevel = -1
            var runFrom = 0
            for (col in 0..tiles.cols) {
                val level = if (col == tiles.cols) -1 else {
                    val a = tileAlpha(tiles.start[row * tiles.cols + col], e)
                    if (a >= 1f) -1 else (a * 3f + 0.5f).toInt().coerceIn(0, 2)
                }
                if (level == runLevel) continue
                if (runLevel >= 0) {
                    paths[runLevel].addRect(Rect(Offset(runFrom * tiles.tile, row * tiles.tile), Size((col - runFrom) * tiles.tile, tiles.tile)))
                }
                runLevel = level
                runFrom = col
            }
        }
        paths.forEachIndexed { level, path -> drawPath(path, bg.copy(alpha = bg.alpha * (1f - level / 3f))) }
    }

    /** 혜성이 날아가는 동안의 위치 — 소실점에서 헤더 별로 가속. */
    fun DrawScope.drawComet(e: Float, target: Offset, color: Color) {
        if (e <= 0f) return
        val c = center(size)
        val d = target - c
        val rT = d.getDistance().coerceAtLeast(1f)
        val dir = d / rT
        val u = clamp01(e / COMET_LAND)
        val r = rT * u.pow(2.2f)
        var tailR = max(0f, r - max(22f * density, r * 0.4f))
        if (e >= COMET_LAND) {
            val since = clamp01((e - COMET_LAND) / 0.06f)
            tailR += (r - tailR) * since
            if (since >= 1f) return
        }
        val head = c + dir * r
        val tail = c + dir * tailR
        drawLine(Brush.linearGradient(listOf(Color.Transparent, color.copy(alpha = 0.85f)), tail, head), tail, head, 2f * density, StrokeCap.Round)
        drawPath(sparklePath(head, (2.5f + 3f * u) * density), color)
    }

    /**
     * 헤더 별 자리 — 혜성이 닿기 전에는 덮어 두고, 닿으면 커졌다 돌아오며 90도 돈다(빛무리). [sinceLandMs] 가 음수면 아직 닿지 않음.
     */
    fun DrawScope.drawHeaderStar(star: Rect, sinceLandMs: Long, bg: Color, color: Color) {
        if (sinceLandMs >= PULSE_MS) return
        val pad = 3f * density
        drawRect(bg, Offset(star.left - pad, star.top - pad), Size(star.width + 2 * pad, star.height + 2 * pad))
        if (sinceLandMs < 0) return
        val k = clamp01(sinceLandMs / PULSE_MS.toFloat())
        val pulse = sin(PI.toFloat() * k)
        val c = star.center
        val r = star.minDimension / 2
        if (pulse > 0f) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.6f * pulse), Color.Transparent), c, r * 3.5f), r * 3.5f, c)
        rotate(90f * easeOut(k), c) { drawPath(sparklePath(c, r * (1f + 0.6f * pulse)), color) }
    }
}

/**
 * 행성과 배경 별을 그린다. 전환 중([e] > 0)에는 행성 중심을 소실점으로 빛줄기가 되고, [skip] 입자는 박히는 빛([PlanetScene.Builder])이 대신 그린다.
 */
internal class PlanetPainter {
    private val starDots = DotBatch(lines = false)
    private val starLines = DotBatch(lines = true)
    private val dots = DotBatch(lines = false)
    private val lines = DotBatch(lines = true)
    private val streaks = DotBatch(lines = true)

    fun draw(scope: DrawScope, tMs: Long, e: Float, color: Color, skip: BooleanArray? = null) = with(scope) {
        val c = PlanetScene.center(size)
        val r = PlanetScene.radius(size)
        val assemble = easeOut(clamp01(tMs / PlanetScene.ASSEMBLE_MS.toFloat()))
        val w = easeIn(clamp01(e / 0.7f))
        val z = 1f - 0.9f * w
        val trail = 0.03f + 0.22f * w
        val fade = 1f - clamp01((e - 0.45f) / 0.5f)
        fun project(p: Offset, zz: Float) = Offset(c.x + (p.x - c.x) / zz, c.y + (p.y - c.y) / zz)
        val stroke = 1.1f * density * (1f + 0.6f * w)

        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f * assemble * (1f - w)), Color.Transparent), c, r * 2.2f), r * 2.2f, c)
        val starAlpha = (0.28f + 0.4f * w) * assemble * fade
        BackgroundStars.points.forEach { s ->
            val base = Offset(s.x * size.width, s.y * size.height)
            if (e <= 0f) starDots.point(starAlpha, base) else starLines.line(starAlpha, project(base, z + trail), project(base, z))
        }
        starDots.flush(this, color, 2.2f * density)
        starLines.flush(this, color, stroke * 0.6f)

        val rw = if (e > 0f) windowRadius(e, size, c, r) else 0f
        val push = easeOut(clamp01(e / 0.35f))
        PlanetScene.particles.forEachIndexed { idx, p ->
            if (e > 0f && skip != null && skip[idx]) return@forEachIndexed
            val (hp, depth) = PlanetScene.home(p, tMs, r, c)
            val sx = p.sx * size.width * 0.6f
            val sy = p.sy * size.height * 0.6f
            val home = Offset(c.x + sx + (hp.x - c.x - sx) * assemble, c.y + sy + (hp.y - c.y - sy) * assemble)
            // 전환이 시작되면 입자는 행성 창 바깥으로 밀려나며 흩어진다 — 사라지지 않고 비켜선다.
            val base = if (e <= 0f) home else {
                val d = home - c
                val dist = d.getDistance().coerceAtLeast(0.001f)
                val target = max(dist, rw * 0.95f + dist * (0.5f + 0.45f * p.sp))
                c + d * ((dist + (target - dist) * push) / dist)
            }
            if (p.kind == 1 && depth < 0 && (base - c).getDistance() < r && e <= 0f) return@forEachIndexed
            val alpha = (if (depth >= 0) 0.95f else if (p.kind == 0) 0.25f else 0.45f) * fade * (1f - 0.6f * w)
            if (e <= 0f) dots.point(alpha, base) else lines.line(alpha, project(base, z + trail), project(base, z))
        }
        dots.flush(this, color, 2.2f * density)
        lines.flush(this, color, stroke)
    }

    /** 박히는 빛 — 워프 빛줄기처럼 뻗다가 목표 자리에서 멈추고, 꼬리가 빨려 들어가며 짧게 반짝인다. */
    fun drawBuilders(scope: DrawScope, list: List<PlanetScene.Builder>, e: Float, color: Color) = with(scope) {
        if (e <= 0f) return@with
        val c = PlanetScene.center(size)
        val w = easeIn(clamp01(e / 0.7f))
        val z = 1f - 0.9f * w
        val trail = 0.03f + 0.22f * w
        val stroke = 1.1f * density * (1f + 0.6f * w)
        list.forEach { b ->
            val rT = (b.target - c).getDistance().coerceAtLeast(1f)
            val dirT = (b.target - c) / rT
            val rS = b.r0 + (b.rStart - b.r0) * easeOut(clamp01(e / 0.15f))
            fun at(rr: Float): Offset {
                val m = clamp01(rr / rT).let { it * it }
                val d = Offset(b.dir0.x + (dirT.x - b.dir0.x) * m, b.dir0.y + (dirT.y - b.dir0.y) * m)
                return c + d * (rr / d.getDistance().coerceAtLeast(0.001f))
            }
            val head = min(rS / z, rT)
            var tail = min(rS / (z + trail), head)
            if (e >= b.eLand) {
                val since = clamp01((e - b.eLand) / 0.06f)
                tail += (rT - tail) * since
                if (since >= 1f) return@forEach
                drawCircle(color.copy(alpha = 0.9f * (1f - since)), (1.2f + 2.2f * since) * density, b.target)
            }
            streaks.line(0.75f, at(tail), at(head))
        }
        streaks.flush(this, color, stroke * 0.85f)
    }

    private fun windowRadius(e: Float, size: Size, c: Offset, r0: Float): Float {
        val rMax = (Offset(0f, size.height) - c).getDistance() * 1.05f
        return r0 + (rMax - r0) * easeIn(e)
    }
}

/** 배경에 희미하게 깔리는 별(두 장면 공통). */
internal object BackgroundStars {
    val points: List<Offset> = run { val r = Random(11); List(420) { Offset(r.nextFloat(), r.nextFloat()) } }
}
