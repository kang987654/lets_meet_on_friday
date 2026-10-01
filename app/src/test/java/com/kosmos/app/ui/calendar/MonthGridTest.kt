package com.kosmos.app.ui.calendar

import com.kosmos.app.domain.model.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * [MonthGridTest]
 * 월 그리드의 주 수·첫 칸·마지막 칸을 달의 시작 요일·일수·윤년별 표로 고정합니다 (0.28.0).
 */
class MonthGridTest {

    private data class Case(val month: String, val weeks: Int, val first: String, val last: String)

    @Test
    fun `주 수와 앞뒤 채움 날짜가 달마다 맞다`() {
        val cases = listOf(
            Case("2026-10", 5, "2026-09-27", "2026-10-31"), // 목요일 시작, 토요일 끝
            Case("2026-02", 4, "2026-02-01", "2026-02-28"), // 일요일 시작 28일 — 채움 없음
            Case("2028-02", 5, "2028-01-30", "2028-03-04"), // 윤년 29일
            Case("2026-08", 6, "2026-07-26", "2026-09-05"), // 토요일 시작 31일 — 6주
            Case("2026-05", 6, "2026-04-26", "2026-06-06"), // 금요일 시작 31일 — 6주
        )
        cases.forEach { c ->
            val grid = monthGrid(YearMonth.parse(c.month))
            assertEquals("${c.month} 주 수", c.weeks, grid.size)
            assertTrue("${c.month} 주마다 7일", grid.all { it.size == 7 })
            assertEquals("${c.month} 첫 칸", LocalDate.parse(c.first), grid.first().first())
            assertEquals("${c.month} 마지막 칸", LocalDate.parse(c.last), grid.last().last())
            assertTrue("${c.month} 첫 칸은 일요일", grid.all { it.first().dayOfWeek == DayOfWeek.SUNDAY })
        }
    }

    @Test
    fun `월요일 시작도 같은 규칙으로 만든다`() {
        val grid = monthGrid(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 9, 28), grid.first().first())
        assertEquals(LocalDate.of(2026, 11, 1), grid.last().last())
    }

    @Test
    fun `일정은 시작 날짜별로 묶이고 해석 못 한 일정은 빠진다`() {
        val utc = ZoneId.of("UTC")
        fun ev(id: String, start: String) = CalendarEvent(id, id, start, start)
        val map = eventsByDate(
            listOf(ev("a", "2026-10-02T09:00:00"), ev("b", "2026-10-02T19:00:00"), ev("c", "2026-10-03"), ev("bad", "내일")),
            utc
        )
        assertEquals(listOf("a", "b"), map[LocalDate.of(2026, 10, 2)]?.map { it.id })
        assertEquals(listOf("c"), map[LocalDate.of(2026, 10, 3)]?.map { it.id })
        assertEquals(2, map.size)
    }

    // --- 여러 날 일정 (0.33.0) ---

    @Test
    fun `일정이 걸친 날짜 표`() {
        val utc = ZoneId.of("UTC")
        fun span(start: String, end: String) = spanDays(CalendarEvent("x", "x", start, end), utc).map { it.toString() }
        val cases = listOf(
            Triple("2026-10-03T10:00:00", "2026-10-05T18:00:00", listOf("2026-10-03", "2026-10-04", "2026-10-05")),
            Triple("2026-10-03T18:00:00", "2026-10-04T00:00:00", listOf("2026-10-03")),          // 자정에 끝나면 다음 날 제외
            Triple("2026-10-03", "2026-10-04", listOf("2026-10-03", "2026-10-04")),             // 종일: 끝은 포함 날짜
            Triple("2026-10-03", "2026-10-03", listOf("2026-10-03")),
            Triple("2026-10-05T10:00:00", "2026-10-03T10:00:00", listOf("2026-10-05")),          // 끝이 앞이면 하루
            Triple("2026-10-03T10:00:00", "끝 모름", listOf("2026-10-03")),
        )
        cases.forEach { (start, end, expected) -> assertEquals("$start ~ $end", expected, span(start, end)) }
        assertEquals("잘못된 끝 날짜로 31일을 넘기지 않는다", 31, span("2026-01-01", "2026-12-31").size)
    }

    @Test
    fun `여러 날 일정은 걸친 날마다 묶인다`() {
        val map = eventsByDate(listOf(CalendarEvent("trip", "제주 여행", "2026-10-03", "2026-10-05")), ZoneId.of("UTC"))
        assertEquals(listOf("2026-10-03", "2026-10-04", "2026-10-05"), map.keys.map { it.toString() }.sorted())
    }
}
