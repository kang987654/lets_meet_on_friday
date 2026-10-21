package com.kosmos.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MotionTest]
 * 움직임 토큰(0.38.4 M3) — 모든 길이는 300ms 아래, "애니메이션 제거"면 화면 전환이 없다.
 */
class MotionTest {

    @Test
    fun `길이 토큰은 모두 300ms 아래`() {
        listOf(Motion.PRESS_MS, Motion.SHORT_MS, Motion.MEDIUM_MS).forEach { assertTrue("$it ms", it < 300) }
    }

    @Test
    fun `애니메이션 제거면 화면 전환이 없다`() {
        val reduced = Motion.NavTransitions(Density(1f), reduced = true)
        assertEquals(EnterTransition.None, reduced.enter())
        assertEquals(ExitTransition.None, reduced.exit())
        assertEquals(EnterTransition.None, reduced.popEnter())
        assertEquals(ExitTransition.None, reduced.popExit())

        val normal = Motion.NavTransitions(Density(1f), reduced = false)
        assertNotEquals(EnterTransition.None, normal.enter())
        assertNotEquals(ExitTransition.None, normal.popExit())
    }
}
