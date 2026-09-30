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
    fun `파싱할 수 없으면 null 이다`() {
        assertNull(IsoDateTimeParser.toDisplayDateKorean("내일 오후", seoul))
    }
}
