package com.kosmos.app.feature.splash

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.kosmos.app.ui.component.sparklePath
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.SplashStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** 장면을 멈춰 그릴 때(애니메이션 제거·카탈로그)의 장면 시각 — 다 모이고 별까지 나온 뒤. */
internal const val STATIC_SCENE_MS = 3000L

/**
 * Ready 뒤 채팅이 덮개 아래에서 자리를 잡을 여유.
 *
 * [WHY] 채팅을 처음 그리는 무거운 프레임들이 워프와 겹치면 전환이 버벅인다(실기기 framestats, 0.38.6 M1) — 장면이 다 모인 채 멈춰
 * 있는 동안에 지나가게 한다.
 */
private const val HANDOFF_MS = 300L

/** 글자가 사라지는 시간(전환 시작부터). */
private const val TEXT_FADE_MS = 200f

private fun assembleMs(style: SplashStyle) = when (style) {
    SplashStyle.PLANET -> PlanetScene.ASSEMBLE_MS
    SplashStyle.GALAXY -> GalaxyScene.ASSEMBLE_MS
}

/** 전환을 시작할 장면 시각 — Ready 직후가 아니라 장면이 다 모인 뒤(사용자 결정). */
internal fun exitStart(style: SplashStyle, readyAt: Long): Long = max(readyAt + HANDOFF_MS, assembleMs(style))

/**
 * 스플래시 장면 한 장 — 입자 장면 + 워드마크·소개·안내 문구. 스플래시 화면과 전환 덮개가 같은 것을 같은 시계로 그린다.
 *
 * [time] 은 장면 시각(ms)으로, 그리기 단계에서만 읽는다(프레임마다 재구성하지 않는다). [exitAt] 이후는 전환이다.
 */
@Composable
internal fun SplashStage(
    style: SplashStyle?,
    time: () -> Long,
    status: String,
    modifier: Modifier = Modifier,
    exitAt: Long = Long.MAX_VALUE,
    planetSkip: BooleanArray? = null,
    // 장면(입자) 층에만 붙는다 — 글자는 따로 그린다.
    sceneModifier: Modifier = Modifier
) {
    val colors = KosmosTheme.colors
    val bg = MaterialTheme.colorScheme.background
    // [WHY] 은하 중심부 — 다크에서는 흰 빛, 라이트에서는 검은 점이 구멍처럼 보여 브랜드 색(시안 확인).
    val core = if (bg.luminance() < 0.5f) colors.textPrimary else colors.brand
    val planet = remember { PlanetPainter() }
    val galaxy = remember { GalaxyPainter() }
    fun exitFade(): Float = 1f - clamp01((time() - exitAt) / TEXT_FADE_MS)

    BoxWithConstraints(modifier.fillMaxSize()) {
        Canvas(sceneModifier.fillMaxSize()) {
            val t = time()
            when (style) {
                SplashStyle.PLANET -> planet.draw(this, t, if (t <= exitAt) 0f else clamp01((t - exitAt) / PlanetScene.WARP_MS.toFloat()), colors.brand, planetSkip)
                SplashStyle.GALAXY -> galaxy.draw(this, t, exitAt, colors.brand, core)
                null -> Unit
            }
        }
        // 장면 중심(높이 36%) 아래 — 행성 고리·은하 팔이 끝나는 자리.
        val top = maxHeight * 0.36f + min(maxWidth.value, maxHeight.value).dp * 0.29f + 24.dp
        Column(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = top),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "KOSMOS",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = TextUnit(6f, TextUnitType.Sp),
                color = colors.textPrimary,
                modifier = Modifier.graphicsLayer {
                    val reveal = clamp01((time() - 600) / 500f)
                    alpha = reveal * exitFade()
                    // 흐림에서 또렷하게 — 흐림 효과는 Android 12 부터라 그 아래는 투명도만.
                    val blur = 8.dp.toPx() * (1f - reveal)
                    renderEffect = if (blur > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BlurEffect(blur, blur, TileMode.Decal) else null
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "기기 안에서만 생각하는 AI 비서",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.graphicsLayer { alpha = clamp01((time() - 900) / 400f) * exitFade() }
            )
            Spacer(Modifier.height(24.dp))
            if (status.isNotEmpty()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.graphicsLayer { alpha = clamp01((time() - 1100) / 400f) * exitFade() }
                )
            }
        }
    }
}

/**
 * [SplashOverlay]
 * 스플래시 장면을 채팅 위로 이어 그리며 진짜 채팅을 드러내는 덮개(0.38.4 M6).
 *
 * ### Architecture Context
 * - **Layer**: UI (셸) — `MainScreen` 이 내비게이션 위에 둔다. [SplashHandoff.exit] 가 있을 때만 그린다.
 *
 * ### Key Flow
 * 1. 스플래시와 같은 장면 시계로 이어 그린다(Ready 순간과 같은 그림).
 * 2. 채팅이 자리를 잡으면 자리를 읽고(행성은 박힐 곳을 계산), 장면이 다 모였으면 전환을 시작한다.
 * 3. 행성: 덮인 칸이 모자이크로 걷히고 혜성이 헤더 별에 앉는다. 은하: 별 자리에서 번지듯 드러나며 채팅이 줌인되고 별이 헤더로 날아간다.
 * 4. 끝나면 [SplashHandoff.finish] — 덮개가 사라진다.
 *
 * 누르기는 막고 화면 읽기에서는 빠진다(1초 남짓, 아래 채팅이 곧 그대로 드러난다).
 */
@Composable
fun SplashOverlay(handoff: SplashHandoff) {
    val exit = handoff.exit ?: return
    val colors = KosmosTheme.colors
    val bg = MaterialTheme.colorScheme.background
    val density = LocalDensity.current.density
    var t by remember(exit) { mutableLongStateOf(exit.readyAt) }
    var exitAt by remember(exit) { mutableLongStateOf(Long.MAX_VALUE) }
    var size by remember { mutableStateOf(Size.Zero) }
    var plan by remember(exit) { mutableStateOf<OverlayPlan?>(null) }
    val painter = remember { PlanetPainter() }

    LaunchedEffect(exit) {
        // 채팅이 덮개 아래에서 한 번 배치될 때까지.
        repeat(2) { withFrameMillis { t = exit.clock.tick(it) } }
        val screen = Rect(Offset.Zero, size)
        val star = handoff.anchorsOf(SplashAnchor.HEADER_STAR, screen).firstOrNull()
            ?: Rect(Offset(140f * density, 40f * density), Size(12f * density, 12f * density))
        val targets = buildList {
            handoff.anchorsOf(SplashAnchor.TITLE, screen).forEach { add(it to 1f) }
            handoff.anchorsOf(SplashAnchor.BUBBLE, screen).forEach { add(it to 1f) }
            handoff.anchorsOf(SplashAnchor.EMPTY, screen).forEach { add(it to 1f) }
            handoff.anchorsOf(SplashAnchor.INPUT_BAR, screen).forEach { add(it to 0.4f) }
        }
        handoff.stopCollecting()
        val planned = exitStart(exit.style, exit.readyAt)
        plan = when (exit.style) {
            SplashStyle.PLANET -> withContext(Dispatchers.Default) {
                val list = PlanetScene.builders(size, targets, planned, density)
                val skip = BooleanArray(PlanetScene.particles.size).also { s -> list.forEach { s[it.idx] = true } }
                OverlayPlan(star, list, skip, PlanetScene.tiles(size, list, density))
            }
            SplashStyle.GALAXY -> OverlayPlan(star, emptyList(), null, null)
        }
        val end = when (exit.style) {
            SplashStyle.PLANET -> PlanetScene.END_MS
            SplashStyle.GALAXY -> GalaxyScene.END_MS
        }
        while (true) {
            val now = withFrameMillis { exit.clock.tick(it) }
            t = now
            if (exitAt == Long.MAX_VALUE && now >= planned) exitAt = now
            val te = now - exitAt
            if (exit.style == SplashStyle.GALAXY && exitAt != Long.MAX_VALUE) {
                handoff.chatOrigin = GalaxyScene.star(size, now, exitAt)
                handoff.chatScale = GalaxyScene.chatScale(te.toFloat())
            }
            if (exitAt != Long.MAX_VALUE && te >= end) break
        }
        handoff.finish()
    }

    val current = plan
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it.toSize() }
            .clearAndSetSemantics { }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
    ) {
        when (exit.style) {
            SplashStyle.PLANET -> {
                SplashStage(
                    style = exit.style, time = { t }, status = exit.status, exitAt = exitAt, planetSkip = current?.skip,
                    modifier = Modifier.drawBehind {
                        with(PlanetScene) { drawTiles(current?.tiles, planetE(t, exitAt), bg) }
                    }
                )
                Canvas(Modifier.fillMaxSize()) {
                    if (current == null) return@Canvas
                    val e = planetE(t, exitAt)
                    painter.drawBuilders(this, current.builders, e, colors.brand)
                    with(PlanetScene) {
                        drawComet(e, current.star.center, colors.brand)
                        val sinceLand = if (exitAt == Long.MAX_VALUE) -1L else t - exitAt - (COMET_LAND * WARP_MS).toLong()
                        drawHeaderStar(current.star, sinceLand, bg, colors.brand)
                    }
                }
            }
            SplashStyle.GALAXY -> {
                // [WHY] 오프스크린 층 + DstOut 가림막 대신 원 자르기 — 그 층이 덮개가 떠 있는 내내 합성돼 은하가 열리는 동안 프레임이 26~53ms 였다
                // (실기기 framestats, 0.38.7 M3). 번지는 경계는 구멍 둘레에 바탕색을 진하게→투명으로 덧칠해 장면이 구멍 쪽으로 흐려지게 한다.
                // 가림은 장면 층에만 — 글자는 전환 200ms 안에 사라지고 그때 드러난 자리는 별 둘레 몇 px 라 겹치지 않는다.
                val hole = remember { Path() }
                SplashStage(
                    style = exit.style, time = { t }, status = exit.status, exitAt = exitAt,
                    sceneModifier = Modifier.drawWithContent {
                        val te = if (exitAt == Long.MAX_VALUE) -1f else (t - exitAt).toFloat()
                        val open = if (te < 0f) 0f else GalaxyScene.open(te)
                        // 다 열린 뒤에는 장면이 전부 가려지므로 그리지 않는다.
                        if (open >= 1f) return@drawWithContent
                        if (open <= 0f) {
                            drawRect(bg)
                            drawContent()
                            return@drawWithContent
                        }
                        val vp = GalaxyScene.star(size, t, exitAt)
                        val r = GalaxyScene.revealRadius(size, vp, te)
                        hole.reset()
                        hole.addOval(Rect(vp, r * REVEAL_CLEAR))
                        clipPath(hole, ClipOp.Difference) {
                            drawRect(bg)
                            this@drawWithContent.drawContent()
                            drawCircle(Brush.radialGradient(REVEAL_CLEAR to bg, 1f to bg.copy(alpha = 0f), center = vp, radius = r), radius = r, center = vp)
                        }
                    }
                )
                Canvas(Modifier.fillMaxSize()) {
                    if (current == null || exitAt == Long.MAX_VALUE) return@Canvas
                    val te = (t - exitAt).toFloat()
                    val vp = GalaxyScene.star(size, t, exitAt)
                    // 채팅의 진짜 헤더 별은 날아온 별이 앉을 때까지 덮어 둔다 — 채팅 배율을 따라 자리가 움직인다.
                    val k = GalaxyScene.chatScale(te)
                    val shown = Rect(vp + (current.star.topLeft - vp) * k, current.star.size * k)
                    val pad = 3f * density
                    drawRect(bg, Offset(shown.left - pad, shown.top - pad), Size(shown.width + 2 * pad, shown.height + 2 * pad))
                    val (pos, r, halo) = GalaxyScene.flyingStar(vp, current.star.center, current.star.minDimension / 2, te, density)
                    val hr = r * (3.2f - 1.8f * (1f - halo) / 0.7f)
                    drawCircle(Brush.radialGradient(listOf(colors.brand.copy(alpha = 0.45f * halo), Color.Transparent), pos, hr), hr, pos)
                    drawPath(sparklePath(pos, r), colors.brand)
                }
            }
        }
    }
}

/** 은하 창의 또렷한 안쪽 비율 — 그 밖은 바깥으로 흐려진다. */
private const val REVEAL_CLEAR = 0.55f

private fun planetE(t: Long, exitAt: Long): Float =
    if (exitAt == Long.MAX_VALUE || t <= exitAt) 0f else clamp01((t - exitAt) / PlanetScene.WARP_MS.toFloat())

private class OverlayPlan(
    val star: Rect,
    val builders: List<PlanetScene.Builder>,
    val skip: BooleanArray?,
    val tiles: PlanetScene.Tiles?
)
