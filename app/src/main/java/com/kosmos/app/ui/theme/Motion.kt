package com.kosmos.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * 움직임 토큰 — 길이·곡선·화면 전환의 단일 출처(DESIGN.md Motion, 0.38.4 M3). 화면에서 `tween(숫자)` 를 직접 쓰지 않는다.
 *
 * [WHY] 강한 ease-out 하나를 기본으로 — 시작이 빨라 눌렀을 때 바로 반응해 보인다. ease-in 은 쓰지 않는다(emil-design-eng).
 * 길이는 자주 보는 것일수록 짧게: 누름 120, 작은 것 150, 화면 전환 250 — 모두 300 아래.
 */
object Motion {
    /** 기본 곡선 — `cubic-bezier(0.23, 1, 0.32, 1)`. */
    val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** 누름 반응. */
    const val PRESS_MS = 120

    /** 작은 상태 바뀜(아이콘 전환·탭 칸). */
    const val SHORT_MS = 150

    /** 화면 전환·결과 등장. */
    const val MEDIUM_MS = 250

    fun <T> press(): FiniteAnimationSpec<T> = tween(PRESS_MS, easing = EaseOut)
    fun <T> short(): FiniteAnimationSpec<T> = tween(SHORT_MS, easing = EaseOut)
    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM_MS, easing = EaseOut)

    /** 화면 전환이 옆으로 미끄러지는 거리(공유 축 — 사용자 선택 B). 화면 전체가 아니라 살짝. */
    private val NAV_SHIFT = 30.dp

    /**
     * 하위 화면 전환(Material 공유 축 X) — 들어갈 때 새 화면이 오른쪽 30dp 에서, 뒤로 올 때는 왼쪽에서. 나가는 화면은 반대로 비키며 더 빨리 사라진다.
     * [reduced] 면 전환 없이 바로 바뀐다(시스템 "애니메이션 제거").
     */
    class NavTransitions(density: Density, private val reduced: Boolean) {
        private val shift = with(density) { NAV_SHIFT.roundToPx() }

        fun enter(): EnterTransition = if (reduced) EnterTransition.None else
            slideInHorizontally(tween(MEDIUM_MS, easing = EaseOut)) { shift } + fadeIn(tween(MEDIUM_MS, easing = EaseOut))

        fun exit(): ExitTransition = if (reduced) ExitTransition.None else
            slideOutHorizontally(tween(MEDIUM_MS, easing = EaseOut)) { -shift } + fadeOut(tween(SHORT_MS, easing = EaseOut))

        fun popEnter(): EnterTransition = if (reduced) EnterTransition.None else
            slideInHorizontally(tween(MEDIUM_MS, easing = EaseOut)) { -shift } + fadeIn(tween(MEDIUM_MS, easing = EaseOut))

        fun popExit(): ExitTransition = if (reduced) ExitTransition.None else
            slideOutHorizontally(tween(MEDIUM_MS, easing = EaseOut)) { shift } + fadeOut(tween(SHORT_MS, easing = EaseOut))
    }
}

