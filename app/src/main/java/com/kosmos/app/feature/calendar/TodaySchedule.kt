package com.kosmos.app.feature.calendar

import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.util.IsoDateTimeParser
import java.time.LocalDate
import java.time.ZoneId

/**
 * 일정 화면 "오늘" 탭이 그릴 내용(0.38.3 M1, Things 3 의 오늘 화면 모티브).
 *
 * @property timeline 오늘 일정과 오늘 울릴 알림을 시간순으로 섞은 것.
 * @property tasks 날짜도 알림도 없는 남은 할 일 — 오늘 할 일 목록 아래에 체크 원으로.
 * @property upcoming 내일부터 이번 주(오늘 포함 7일) 끝까지의 일정.
 */
data class TodayModel(
    val timeline: List<TodayEntry>,
    val tasks: List<TaskItem>,
    val upcoming: List<UpcomingEntry>,
    val deviceCalendarFailed: Boolean
) {
    val isEmpty: Boolean get() = timeline.isEmpty() && tasks.isEmpty()
}

/** 오늘 묶음의 한 줄 — 일정 또는 알림. [sortKey] 는 시작·알림 시각(종일 일정은 하루 맨 앞). */
sealed interface TodayEntry {
    val sortKey: Long
    val timeLabel: String
    val title: String

    data class Event(val event: CalendarEvent, override val sortKey: Long, override val timeLabel: String, val endLabel: String?) : TodayEntry {
        override val title: String get() = event.title
    }

    data class Reminder(val task: TaskItem, override val sortKey: Long, override val timeLabel: String) : TodayEntry {
        override val title: String get() = task.title
    }
}

/** 다가오는 일정 한 줄 — "16일 금" · 제목 · 시각. */
data class UpcomingEntry(val event: CalendarEvent, val dayLabel: String, val timeLabel: String)

/**
 * [TodayModel] 을 만드는 순수 함수 — 오늘 판정은 인자로 받는다(AGENTS §2-④). 표기는 [IsoDateTimeParser] 단일 출처(§4-7).
 */
object TodayComposer {

    fun compose(week: ScheduleData?, reminders: List<TaskItem>, pending: List<TaskItem>, today: LocalDate, zone: ZoneId): TodayModel {
        val events = week?.events.orEmpty()
        val todayEvents = events.filter { IsoDateTimeParser.toLocalDate(it.startIso, zone) == today }.map { event ->
            TodayEntry.Event(
                event = event,
                sortKey = sortKeyOf(event.startIso, today, zone),
                timeLabel = timeOf(event.startIso, zone),
                endLabel = event.endIso.takeIf { !IsoDateTimeParser.isDateOnly(it) && it != event.startIso }
                    ?.let { IsoDateTimeParser.toDisplayTimeKorean(it, zone) }?.let { "${it}까지" }
            )
        }
        val todayReminders = reminders.mapNotNull { task ->
            val iso = task.remindAtIso ?: return@mapNotNull null
            if (IsoDateTimeParser.toLocalDate(iso, zone) != today) return@mapNotNull null
            TodayEntry.Reminder(task, sortKeyOf(iso, today, zone), timeOf(iso, zone))
        }
        // [WHY] 할 일 목록에는 날짜·알림이 없는 것만 — 일정(dueDate)과 알림(remindAt)은 위 시간순 묶음이 맡는다(같은 일을 두 번 보이지 않게).
        val plainTasks = pending.filter { it.remindAtIso == null && it.dueDateIso == null && !it.isCompleted }
        val upcoming = events
            .mapNotNull { event -> IsoDateTimeParser.toLocalDate(event.startIso, zone)?.let { it to event } }
            .filter { (date, _) -> date.isAfter(today) }
            .sortedBy { (_, event) -> IsoDateTimeParser.toEpochMillis(event.startIso, zone) ?: Long.MAX_VALUE }
            .map { (date, event) -> UpcomingEntry(event, IsoDateTimeParser.dayWeekdayKorean(date), timeOf(event.startIso, zone)) }
        return TodayModel(
            timeline = (todayEvents + todayReminders).sortedBy { it.sortKey },
            tasks = plainTasks,
            upcoming = upcoming,
            deviceCalendarFailed = week?.deviceCalendarFailed == true
        )
    }

    private fun timeOf(iso: String, zone: ZoneId): String = IsoDateTimeParser.toDisplayTimeKorean(iso, zone) ?: ALL_DAY

    private fun sortKeyOf(iso: String, today: LocalDate, zone: ZoneId): Long =
        if (IsoDateTimeParser.isDateOnly(iso)) today.atStartOfDay(zone).toInstant().toEpochMilli() - 1
        else IsoDateTimeParser.toEpochMillis(iso, zone) ?: Long.MAX_VALUE

    private const val ALL_DAY = "종일"
}
