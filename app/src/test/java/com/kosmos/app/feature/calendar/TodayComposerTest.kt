package com.kosmos.app.feature.calendar

import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.model.TaskItem
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 일정 "오늘" 탭의 묶음(0.38.3 M1) — 오늘 일정·알림을 시간순으로 섞고, 할 일은 날짜·알림 없는 것만, 다가오는 일정은 내일부터.
 * 날짜·시간대 고정(AGENTS §2-④).
 */
class TodayComposerTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 15)

    private fun event(id: String, start: String, end: String = start, source: CalendarEvent.Source = CalendarEvent.Source.APP) =
        CalendarEvent(id = id, title = id, startIso = start, endIso = end, source = source)

    private fun week(vararg events: CalendarEvent, failed: Boolean = false) =
        ScheduleData(events = events.toList().toImmutableList(), summary = null, rangeType = ScheduleData.RangeType.WEEK, deviceCalendarFailed = failed)

    @Test
    fun `오늘 일정과 오늘 울릴 알림이 시간순으로 섞인다 — 다른 날 알림은 빠진다`() {
        val model = TodayComposer.compose(
            week(event("치과", "2026-10-15T15:00:00", "2026-10-15T16:00:00"), event("회의", "2026-10-19T10:00:00")),
            reminders = listOf(
                TaskItem(id = "물", title = "물 마시기", isCompleted = false, createdAt = 0, remindAtIso = "2026-10-15T13:56:00"),
                TaskItem(id = "약", title = "약 먹기", isCompleted = false, createdAt = 0, remindAtIso = "2026-10-16T21:00:00")
            ),
            pending = emptyList(), today = today, zone = zone
        )

        assertEquals(listOf("물 마시기", "치과"), model.timeline.map { it.title })
        assertEquals("오후 1:56", model.timeline[0].timeLabel)
        assertEquals("오후 4:00까지", (model.timeline[1] as TodayEntry.Event).endLabel)
    }

    @Test
    fun `할 일은 날짜도 알림도 없는 것만 — 일정·알림을 두 번 보이지 않는다`() {
        val model = TodayComposer.compose(
            week(), reminders = emptyList(),
            pending = listOf(
                TaskItem(id = "a", title = "장보기", isCompleted = false, createdAt = 0),
                TaskItem(id = "b", title = "물 마시기", isCompleted = false, createdAt = 0, remindAtIso = "2026-10-15T13:56:00"),
                TaskItem(id = "c", title = "팀 회의", isCompleted = false, createdAt = 0, dueDateIso = "2026-10-19T10:00:00")
            ),
            today = today, zone = zone
        )

        assertEquals(listOf("장보기"), model.tasks.map { it.title })
    }

    @Test
    fun `다가오는 일정은 내일부터 시각순, 날짜 칸은 '16일 금'`() {
        val model = TodayComposer.compose(
            week(event("회의", "2026-10-19T10:00:00"), event("저녁", "2026-10-16T19:00:00"), event("오늘", "2026-10-15T09:00:00")),
            reminders = emptyList(), pending = emptyList(), today = today, zone = zone
        )

        assertEquals(listOf("저녁", "회의"), model.upcoming.map { it.event.title })
        assertEquals("16일 금", model.upcoming[0].dayLabel)
        assertEquals("오후 7:00", model.upcoming[0].timeLabel)
    }

    @Test
    fun `종일 일정은 맨 앞, 기기 캘린더 실패 플래그는 그대로 전달, 비면 isEmpty`() {
        val model = TodayComposer.compose(
            week(event("휴가", "2026-10-15"), event("점심", "2026-10-15T12:00:00"), failed = true),
            reminders = emptyList(), pending = emptyList(), today = today, zone = zone
        )
        assertEquals(listOf("휴가", "점심"), model.timeline.map { it.title })
        assertEquals("종일", model.timeline[0].timeLabel)
        assertTrue(model.deviceCalendarFailed)

        assertTrue(TodayComposer.compose(null, emptyList(), emptyList(), today, zone).isEmpty)
    }
}
