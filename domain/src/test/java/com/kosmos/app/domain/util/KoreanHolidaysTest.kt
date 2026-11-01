package com.kosmos.app.domain.util

import com.kosmos.app.core.common.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class KoreanHolidaysTest {

    @Test
    fun `양력 고정 공휴일과 음력 공휴일을 한국어와 영어로 돌려준다`() {
        assertEquals("삼일절", KoreanHolidays.nameOf(LocalDate.of(2026, 3, 1)))
        assertEquals("Independence Movement Day", KoreanHolidays.nameOf(LocalDate.of(2026, 3, 1), AppLanguage.EN))

        assertEquals("추석", KoreanHolidays.nameOf(LocalDate.of(2026, 9, 25)))
        assertEquals("Chuseok", KoreanHolidays.nameOf(LocalDate.of(2026, 9, 25), AppLanguage.EN))

        assertEquals("설날", KoreanHolidays.nameOf(LocalDate.of(2026, 2, 17)))
        assertEquals("Lunar New Year", KoreanHolidays.nameOf(LocalDate.of(2026, 2, 17), AppLanguage.EN))

        assertNull(KoreanHolidays.nameOf(LocalDate.of(2026, 11, 2)))
        assertNull(KoreanHolidays.nameOf(LocalDate.of(2026, 11, 2), AppLanguage.EN))
    }
}
