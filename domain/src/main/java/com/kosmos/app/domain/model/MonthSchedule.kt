package com.kosmos.app.domain.model

import kotlinx.collections.immutable.ImmutableList
import java.time.YearMonth

/**
 * 한 달치 일정 조회 결과입니다 (월 캘린더, 0.28.0).
 *
 * [WHY] [ScheduleData] 를 재사용하지 않는다 — 그 `rangeType`(오늘/이번 주)에 맞는 값이 없고, 툴·브리핑·위젯이
 * 공유하는 enum 에 MONTH 를 넣으면 `when` 분기 7곳이 함께 흔들린다. 월 조회에 요약은 없다(사용자 결정).
 */
data class MonthSchedule(
    val month: YearMonth,
    val events: ImmutableList<CalendarEvent>,
    val deviceCalendarFailed: Boolean = false
)
