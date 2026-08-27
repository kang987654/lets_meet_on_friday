package com.kosmos.app.widget

import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.model.TaskItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * [WidgetSnapshotTest]
 * 위젯 재료 조립의 데이터 정확성을 고정합니다 — Glance 렌더는 JVM 게이트가 못 보므로
 * 이 순수 함수가 정확성의 전부를 진다. 시간대는 UTC 명시(결정성 수칙).
 */
class WidgetSnapshotTest {

    private val utc = ZoneOffset.UTC

    private fun event(title: String, startIso: String) = CalendarEvent(
        id = title, title = title, startIso = startIso, endIso = startIso,
        location = null, description = null
    )

    private fun schedule(vararg events: CalendarEvent, calendarFailed: Boolean = false) =
        ScheduleData(
            events = events.toList().toPersistentList(),
            summary = null,
            rangeType = ScheduleData.RangeType.TODAY,
            deviceCalendarFailed = calendarFailed
        )

    private fun task(id: String, done: Boolean) = TaskItem(
        id = id, title = id, isCompleted = done, createdAt = 0L
    )

    @Test
    fun `일정은 3건까지 시각과 함께 담기고 초과분은 카운트로 남는다`() {
        val snapshot = buildWidgetSnapshot(
            schedule(
                event("아침 회의", "2026-08-28T09:00:00"),
                event("점심", "2026-08-28T12:30:00"),
                event("치과", "2026-08-28T15:00:00"),
                event("저녁 약속", "2026-08-28T19:00:00")
            ),
            pendingTasks = emptyList(),
            nowMs = 0L,
            zone = utc
        )

        assertEquals(3, snapshot.eventLines.size)
        assertEquals(WidgetEventLine("오전 9:00", "아침 회의"), snapshot.eventLines[0])
        assertEquals(WidgetEventLine("오후 12:30", "점심"), snapshot.eventLines[1])
        assertEquals(1, snapshot.overflowCount)
    }

    @Test
    fun `종일 일정(날짜-only ISO)은 '종일'로 표기한다`() {
        val snapshot = buildWidgetSnapshot(
            schedule(event("광복절", "2026-08-15")),
            pendingTasks = emptyList(),
            nowMs = 0L,
            zone = utc
        )

        assertEquals("종일", snapshot.eventLines.single().time)
    }

    @Test
    fun `할 일 카운트는 완료 항목을 걸러 센다`() {
        val snapshot = buildWidgetSnapshot(
            schedule(),
            pendingTasks = listOf(task("a", false), task("b", true), task("c", false)),
            nowMs = 0L,
            zone = utc
        )

        assertEquals(2, snapshot.pendingTaskCount)
        assertTrue(snapshot.eventLines.isEmpty())
        assertEquals(0, snapshot.overflowCount)
    }

    @Test
    fun `조회 실패(null)는 캘린더 확인 실패로 표기한다`() {
        val failed = buildWidgetSnapshot(null, emptyList(), nowMs = 0L, zone = utc)
        val ok = buildWidgetSnapshot(schedule(), emptyList(), nowMs = 0L, zone = utc)

        assertTrue(failed.deviceCalendarFailed)
        assertFalse(ok.deviceCalendarFailed)
    }

    @Test
    fun `날짜 라벨은 한국어 요일을 포함한다`() {
        // 2026-08-28 은 금요일 (프로젝트 명대로).
        val snapshot = buildWidgetSnapshot(
            schedule(), emptyList(),
            nowMs = 1_787_875_200_000L, // 2026-08-28T00:00:00Z
            zone = utc
        )

        assertEquals("8월 28일 금요일", snapshot.dateLabel)
    }
}
