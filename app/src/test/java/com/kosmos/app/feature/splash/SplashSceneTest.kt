package com.kosmos.app.feature.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.kosmos.app.ui.theme.SplashStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SplashSceneTest]
 * 시작 화면 장면(0.38.4 M6)의 순수 계산 — 저장값, 전환 시작 시각, 모자이크 단계, 박히는 빛, 은하 줌인, 자리 알림.
 */
class SplashSceneTest {

    private val screen = Size(1080f, 2340f)

    @Test
    fun `저장값이 없거나 모르는 값이면 행성`() {
        assertEquals(SplashStyle.PLANET, SplashStyle.fromKey(null))
        assertEquals(SplashStyle.PLANET, SplashStyle.fromKey("AURORA"))
        assertEquals(SplashStyle.GALAXY, SplashStyle.fromKey("GALAXY"))
    }

    @Test
    fun `로딩이 빨리 끝나도 장면이 다 모인 뒤 전환한다`() {
        assertEquals(PlanetScene.ASSEMBLE_MS, exitStart(SplashStyle.PLANET, readyAt = 300))
        assertEquals(GalaxyScene.ASSEMBLE_MS, exitStart(SplashStyle.GALAXY, readyAt = 300))
        assertTrue(exitStart(SplashStyle.PLANET, readyAt = 9_000) in 9_001..9_400)
    }

    @Test
    fun `모자이크 칸은 세 단계로 켜진다`() {
        val start = 0.5f
        assertEquals(0f, PlanetScene.tileAlpha(start, 0.4f), 0f)
        val steps = listOf(0.5f, 0.53f, 0.56f, 0.6f).map { PlanetScene.tileAlpha(start, it) }
        assertEquals(listOf(0f, 1f / 3, 2f / 3, 1f), steps)
    }

    @Test
    fun `빛은 요소마다 박히고 한 입자는 한 번만 쓴다`() {
        val targets = listOf(
            Rect(Offset(40f, 80f), Size(300f, 60f)) to 1f,
            Rect(Offset(40f, 2100f), Size(1000f, 150f)) to 0.4f
        )
        val list = PlanetScene.builders(screen, targets, exitAtMs = 2400, density = 2.75f)
        assertTrue(list.size >= 16)
        assertEquals(list.size, list.map { it.idx }.toSet().size)
        list.forEach { assertTrue(it.eLand in 0f..0.7f) }
        // 입력바 몫을 줄였다 — 넓은 입력바에 빛이 몰리지 않는다.
        val bottom = list.count { it.target.y > 2000f }
        assertTrue("입력바 $bottom / ${list.size}", bottom < list.size * 0.75f)
    }

    @Test
    fun `채팅 요소가 없으면 박히는 빛 없이 모자이크만`() {
        assertTrue(PlanetScene.builders(screen, emptyList(), 2400, 2.75f).isEmpty())
        val tiles = PlanetScene.tiles(screen, emptyList(), density = 2.75f)
        assertTrue(tiles.start.all { it in 0.5f..0.56f })
    }

    @Test
    fun `은하 전환은 0점55배에서 다가와 1배로 내려앉고 끝에는 화면을 다 드러낸다`() {
        assertEquals(0.55f, GalaxyScene.chatScale(0f), 0.001f)
        assertEquals(1f, GalaxyScene.chatScale(GalaxyScene.END_MS.toFloat()), 0.001f)
        val vp = GalaxyScene.star(screen, 2000, 2000)
        val r = GalaxyScene.revealRadius(screen, vp, GalaxyScene.END_MS.toFloat())
        val far = listOf(Offset.Zero, Offset(screen.width, 0f), Offset(0f, screen.height), Offset(screen.width, screen.height)).maxOf { (it - vp).getDistance() }
        assertTrue(r * 0.55f >= far)
    }

    @Test
    fun `별은 헤더 별 자리에 그 크기로 앉는다`() {
        val target = Offset(300f, 120f)
        val (pos, r, _) = GalaxyScene.flyingStar(Offset(500f, 800f), target, 16f, GalaxyScene.END_MS.toFloat(), 2.75f)
        assertEquals(target.x, pos.x, 0.01f)
        assertEquals(target.y, pos.y, 0.01f)
        assertEquals(16f, r, 0.01f)
    }

    @Test
    fun `장면 시계는 보통 프레임은 그대로 더하고 몇 초 멈춘 뒤에는 건너뛰지 않는다`() {
        val clock = SceneClock()
        assertEquals(0L, clock.tick(1_000))
        assertEquals(16L, clock.tick(1_016))
        // GPU 가중치 업로드로 2.6초 멈춘 뒤 — 한 걸음만 간다.
        assertEquals(16L + SceneClock.MAX_STEP_MS, clock.tick(3_616))
        assertEquals(16L + SceneClock.MAX_STEP_MS + 8L, clock.tick(3_624))
    }

    @Test
    fun `전환은 한 번만 시작하고 자리 받기를 멈추면 더 받지 않는다`() {
        val h = SplashHandoff()
        val first = SplashExit(SplashStyle.PLANET, SceneClock(), 1500, "")
        h.begin(first)
        h.begin(SplashExit(SplashStyle.GALAXY, SceneClock(), 1600, ""))
        assertEquals(first, h.exit)

        val owner = Any()
        h.report(owner, SplashAnchor.TITLE, Rect(0f, 0f, 10f, 10f))
        h.report(Any(), SplashAnchor.TITLE, Rect(-50f, -50f, -10f, -10f)) // 화면 밖
        assertEquals(1, h.anchorsOf(SplashAnchor.TITLE, Rect(0f, 0f, 100f, 100f)).size)
        h.stopCollecting()
        h.report(Any(), SplashAnchor.TITLE, Rect(20f, 20f, 30f, 30f))
        assertEquals(1, h.anchorsOf(SplashAnchor.TITLE, Rect(0f, 0f, 100f, 100f)).size)

        h.finish()
        assertNull(h.exit)
        assertEquals(1f, h.chatScale, 0f)
    }
}
