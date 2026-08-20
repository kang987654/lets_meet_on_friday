package com.kosmos.app.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * [BriefingDelayCalculationTest]
 * 다음 알림 시각까지의 지연 계산을 고정합니다 — 시간대를 UTC 로 명시해 결정적이다.
 */
class BriefingDelayCalculationTest {

    private val zone = ZoneOffset.UTC
    private val briefing = 9 * 60 + 17 // 09:17

    private fun at(hour: Int, minute: Int): Long =
        LocalDate.of(2026, 8, 21).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `시각 전이면 오늘 시각까지의 지연이다`() {
        val delay = nextTriggerDelayMillis(at(8, 17), briefing, zone)
        assertEquals(60 * 60_000L, delay) // 1시간
    }

    @Test
    fun `시각 정각이면 내일로 넘어간다 - 이미 발화한 시각을 다시 잡지 않는다`() {
        val delay = nextTriggerDelayMillis(at(9, 17), briefing, zone)
        assertEquals(24 * 60 * 60_000L, delay)
    }

    @Test
    fun `시각 이후면 내일 시각까지의 지연이다`() {
        val delay = nextTriggerDelayMillis(at(10, 17), briefing, zone)
        assertEquals(23 * 60 * 60_000L, delay)
    }

    @Test
    fun `자정 직전에서 자정 설정(0분)은 내일 자정까지다`() {
        val delay = nextTriggerDelayMillis(at(23, 59), 0, zone)
        assertEquals(60_000L, delay)
    }
}
