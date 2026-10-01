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

/** 여러 날 일정을 펼칠 최대 일수 — 잘못된 끝 날짜로 한 달을 통째로 칠하지 않게(0.33.0 계획 M-C1). */
const val MAX_EVENT_SPAN_DAYS = 31L

/**
 * 일정이 걸친 날짜들(시작일 ~ 마지막 날, 포함). 시작을 해석하지 못하면 빈 목록.
 *
 * [WHY] 끝 시각이 정확히 자정이면 그날은 포함하지 않는다 — "10/3 18:00 ~ 10/4 00:00"은 10/3 하루 일정이다. 날짜만 있는 끝(종일 일정)은
 * 이미 포함 날짜다(AndroidCalendarTool 이 DTEND − 1일로 낸다). 끝이 없거나 시작보다 앞이면 시작일 하루.
 */
fun spanDays(event: CalendarEvent, zoneId: ZoneId): List<LocalDate> {
    val start = IsoDateTimeParser.toLocalDate(event.startIso, zoneId) ?: return emptyList()
    val endMs = IsoDateTimeParser.toEpochMillis(event.endIso, zoneId)
    var end = IsoDateTimeParser.toLocalDate(event.endIso, zoneId) ?: start
    if (endMs != null && !IsoDateTimeParser.isDateOnly(event.endIso)) {
        val endTime = java.time.Instant.ofEpochMilli(endMs).atZone(zoneId).toLocalTime()
        if (endTime == java.time.LocalTime.MIDNIGHT && end.isAfter(start)) end = end.minusDays(1)
    }
    if (end.isBefore(start)) end = start
    val last = minOf(end, start.plusDays(MAX_EVENT_SPAN_DAYS - 1))
    return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
}

/**
 * 일정을 걸친 날짜마다 묶습니다(0.33.0 — 이전에는 시작일에만). 시작 시각을 해석하지 못한 일정은 빠진다.
 * 기간 막대 표시는 범위 밖이다 — 걸친 날마다 점과 목록으로 보인다(사용자 결정 D-C1).
 */
fun eventsByDate(events: List<CalendarEvent>, zoneId: ZoneId): Map<LocalDate, List<CalendarEvent>> =
    events.flatMap { event -> spanDays(event, zoneId).map { it to event } }
        .groupBy({ it.first }, { it.second })
