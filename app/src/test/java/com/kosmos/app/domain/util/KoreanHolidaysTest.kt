package com.kosmos.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * [KoreanHolidaysTest]
 * 내장 공휴일 표(0.33.0) — 양력 고정·음력·대체공휴일·표 밖 연도의 경계를 고정합니다.
 */
class KoreanHolidaysTest {

    @Test
    fun `공휴일 이름 표`() {
        val cases = listOf(
            "2026-01-01" to "신정",
            "2026-02-17" to "설날",
            "2026-02-18" to "설날 연휴",
            "2026-03-02" to "대체공휴일(삼일절)",  // 3/1 일요일
            "2026-09-25" to "추석",
            "2026-10-05" to "대체공휴일(개천절)",  // 10/3 토요일
            "2026-10-09" to "한글날",
            "2027-02-08" to "대체공휴일(설날)",    // 설 연휴가 일요일과 겹침
            "2027-12-27" to "대체공휴일(성탄절)",
            "2030-05-05" to "어린이날",            // 표 밖 연도도 양력 고정은 보인다
        )
        cases.forEach { (date, name) -> assertEquals(date, name, KoreanHolidays.nameOf(LocalDate.parse(date))) }
    }

    @Test
    fun `평일과 표 밖 연도의 음력 날짜는 공휴일이 아니다`() {
        assertNull(KoreanHolidays.nameOf(LocalDate.parse("2026-10-01")))
        assertNull("표 밖 연도의 음력 공휴일은 모른다", KoreanHolidays.nameOf(LocalDate.parse("2030-02-03")))
        assertNull("현충일은 대체공휴일 대상이 아니다", KoreanHolidays.nameOf(LocalDate.parse("2026-06-08")))
    }
}
