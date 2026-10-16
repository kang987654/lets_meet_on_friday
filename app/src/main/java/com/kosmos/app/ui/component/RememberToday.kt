package com.kosmos.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * 화면이 "오늘"을 읽는 시계. 앱은 기기 시계를 쓰고, 화면 기준 이미지 테스트는 고정 시계를 넣는다.
 *
 * [WHY] 기준 이미지가 실행 날짜에 따라 "오늘/어제"·달력의 오늘 표시가 달라지면 비교가 매일 깨진다(AGENTS §2-④).
 */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.systemDefaultZone() }

/**
 * 화면이 쓰는 "오늘" — 화면이 다시 보일 때(ON_RESUME)마다 다시 읽습니다.
 *
 * [WHY] 컴포저블 본문에서 `LocalDate.now()` 를 부르면 재구성될 때만 갱신돼, 자정을 넘겨 앱으로
 * 돌아와도 일정 화면 헤더·날짜 띠와 타임라인의 "오늘/어제"가 어제 기준으로 남았다. 벽시계는
 * 이 한 곳에서만 읽고([LocalClock]), 판정 함수들은 이 값을 인자로 받는다(AGENTS §2-④).
 */
@Composable
fun rememberToday(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
    val clock = LocalClock.current
    var today by remember { mutableStateOf(LocalDate.now(clock.withZone(zoneId))) }
    LifecycleResumeEffect(zoneId, clock) {
        today = LocalDate.now(clock.withZone(zoneId))
        onPauseOrDispose { }
    }
    return today
}
