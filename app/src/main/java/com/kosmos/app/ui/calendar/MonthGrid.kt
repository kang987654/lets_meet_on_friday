package com.kosmos.app.ui.calendar

import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.util.IsoDateTimeParser
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 월 캘린더 그리드를 만듭니다 — 앞뒤 달 날짜로 채운 **필요한 만큼의 주(4~6)**, 주마다 7일.
 *
 * [WHY] 순수 함수로 둔다(AGENTS §2-④) — 달의 첫 요일·일수·윤년 경계를 벽시계·Compose 없이 표로 검증한다.
 * 첫 요일 기본값은 일요일(사용자 결정, 0.28.0). "다음주 월요일" 계산(PromptAssembler)은 월요일 시작이지만
 * 그것은 상대 날짜 해석 규칙이고 이 그리드는 표시 규칙이라 서로 묶이지 않는다.
 */
fun monthGrid(yearMonth: YearMonth, firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY): List<List<LocalDate>> {
    val start = yearMonth.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val lastDayOfWeek = firstDayOfWeek.minus(1)
    val end = yearMonth.atEndOfMonth().with(TemporalAdjusters.nextOrSame(lastDayOfWeek))
    return generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(end) }
        .chunked(7)
        .toList()
}

/**
 * 일정을 시작 날짜별로 묶습니다. 시작 시각을 해석하지 못한 일정은 빠진다.
 *
 * [WHY] 여러 날에 걸친 일정도 이번 회차는 **시작일에만** 표시한다(계획서 0.28.0 M2) — 기간 막대 표시는 범위 밖.
 */
fun eventsByDate(events: List<CalendarEvent>, zoneId: ZoneId): Map<LocalDate, List<CalendarEvent>> =
    events.mapNotNull { event ->
        IsoDateTimeParser.toEpochMillis(event.startIso, zoneId)?.let { ms ->
            java.time.Instant.ofEpochMilli(ms).atZone(zoneId).toLocalDate() to event
        }
    }.groupBy({ it.first }, { it.second })
