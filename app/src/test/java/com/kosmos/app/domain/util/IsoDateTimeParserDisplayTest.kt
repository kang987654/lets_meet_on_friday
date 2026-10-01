package com.kosmos.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/**
 * [IsoDateTimeParserDisplayTest]
 * 승인 카드가 쓰는 날짜·시각 표기를 고정합니다 — ISO 원문("2026-09-30")이 화면에 새지 않아야 한다.
 */
class IsoDateTimeParserDisplayTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `날짜는 월 일 요일로 표기한다`() {
        assertEquals("9월 30일 (수)", IsoDateTimeParser.toDisplayDateKorean("2026-09-30T10:00:00", seoul))
    }

    @Test
    fun `오프셋이 붙은 시각은 기기 시간대로 옮겨 표기한다`() {
        // 2026-09-30T23:30Z = 서울 10/1 08:30
        assertEquals("10월 1일 (목)", IsoDateTimeParser.toDisplayDateKorean("2026-09-30T23:30:00Z", seoul))
        assertEquals("오전 8:30", IsoDateTimeParser.toDisplayTimeKorean("2026-09-30T23:30:00Z", seoul))
    }

    @Test
    fun `긴 날짜 표기는 예전 DateTimeFormatter 패턴과 바이트가 같다 - 브리핑 프롬프트 불변`() {
        // 한 주 7일 전부 — 요일 이름 표를 손으로 적었으므로 모든 요일을 대조한다.
        val legacy = java.time.format.DateTimeFormatter.ofPattern("M월 d일 EEEE", java.util.Locale.KOREAN)
        (0L until 7L).forEach { offset ->
            val instant = java.time.Instant.parse("2026-09-28T03:00:00Z").plusSeconds(offset * 86_400)
            assertEquals(
                legacy.format(instant.atZone(seoul)),
                IsoDateTimeParser.longDateKorean(instant.toEpochMilli(), seoul)
            )
        }
    }

    @Test
    fun `파싱할 수 없으면 null 이다`() {
        assertNull(IsoDateTimeParser.toDisplayDateKorean("내일 오후", seoul))
    }

    @Test
    fun `날짜만 있는 종일 일정은 시각 대신 종일로 표기한다`() {
        val seoul = java.time.ZoneId.of("Asia/Seoul")
        assertEquals("종일", IsoDateTimeParser.toDisplayTimeKorean("2026-08-15", seoul))
        assertEquals("8월 15일 종일", IsoDateTimeParser.toDisplayKorean("2026-08-15", seoul))
        assertEquals("시각이 있으면 그대로", "오후 3:00", IsoDateTimeParser.toDisplayTimeKorean("2026-08-15T15:00:00", seoul))
    }
}
