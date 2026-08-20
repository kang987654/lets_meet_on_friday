package com.kosmos.app.assistant.briefing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * [BriefingConditionTest]
 * 브리핑 생성 판정 순수 함수의 경계값을 고정합니다 — 시간대를 UTC 로 명시해 실행 환경과
 * 무관하게 결정적이다.
 */
class BriefingConditionTest {

    private val zone = ZoneOffset.UTC

    private fun at(hour: Int, minute: Int): Long =
        LocalDate.of(2026, 8, 21).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `설정 시각 정각에 생성한다 - 경계는 이상(gte)`() {
        assertTrue(shouldGenerateBriefing(true, at(9, 17), 9 * 60 + 17, 0, zone))
    }

    @Test
    fun `설정 시각 1분 전이면 생성하지 않는다`() {
        assertFalse(shouldGenerateBriefing(true, at(9, 16), 9 * 60 + 17, 0, zone))
    }

    @Test
    fun `꺼져 있으면 시각과 무관하게 생성하지 않는다`() {
        assertFalse(shouldGenerateBriefing(false, at(23, 59), 0, 0, zone))
    }

    @Test
    fun `오늘 이미 생성했으면 생성하지 않는다`() {
        assertFalse(shouldGenerateBriefing(true, at(10, 0), 9 * 60 + 17, 1, zone))
    }

    @Test
    fun `자정 설정(0분)은 하루 내내 참이다`() {
        assertTrue(shouldGenerateBriefing(true, at(0, 0), 0, 0, zone))
        assertTrue(shouldGenerateBriefing(true, at(23, 59), 0, 0, zone))
    }
}
