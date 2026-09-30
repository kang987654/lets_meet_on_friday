package com.kosmos.app.widget

import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.util.IsoDateTimeParser
import java.time.ZoneId

/**
 * [WidgetSnapshot]
 * 홈 위젯이 그릴 재료의 스냅샷입니다 (A3). 조립은 top-level 순수 함수 — Glance 렌더와
 * 분리해 JVM 테스트가 데이터 정확성을 진다(위젯 UI 는 JVM 게이트가 못 본다).
 */
data class WidgetSnapshot(
    val dateLabel: String,
    val eventLines: List<WidgetEventLine>,
    val overflowCount: Int,
    val pendingTaskCount: Int,
    val deviceCalendarFailed: Boolean
)

data class WidgetEventLine(val time: String, val title: String)

/** 위젯에 보여줄 일정 최대 개수 — 4x2 위젯 한 화면 분량. */
internal const val WIDGET_MAX_EVENTS = 3

internal fun buildWidgetSnapshot(
    schedule: ScheduleData?,
    pendingTasks: List<TaskItem>,
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault()
): WidgetSnapshot {
    val events = schedule?.events.orEmpty()
    val lines = events.take(WIDGET_MAX_EVENTS).map { event ->
        WidgetEventLine(
            // [WHY] 날짜-only ISO(기기 캘린더 종일 일정)는 "오전 12:00"으로 오해되므로 "종일".
            time = if (!event.startIso.contains('T')) "종일"
            else IsoDateTimeParser.toDisplayTimeKorean(event.startIso, zone) ?: "",
            title = event.title
        )
    }
    val dateLabel = IsoDateTimeParser.longDateKorean(nowMs, zone)
    return WidgetSnapshot(
        dateLabel = dateLabel,
        eventLines = lines,
        overflowCount = (events.size - WIDGET_MAX_EVENTS).coerceAtLeast(0),
        // [WHY] 방어적 필터 — getPendingTasksData 는 지금 DAO 에서 isCompleted = 0 으로 걸러 오지만,
        // 위젯은 계약이 바뀌어도 완료 항목을 "할 일"로 세면 안 된다(필터 비용은 무시할 만하다).
        pendingTaskCount = pendingTasks.count { !it.isCompleted },
        // [WHY] 조회 실패(null)도 true — 위젯은 권한 요청 UI 를 못 띄우므로 이 표기가
        // "일정 없음"과 "확인 못 함"을 가르는 유일한 정직한 신호다 (EC4, ADR-004).
        deviceCalendarFailed = schedule?.deviceCalendarFailed ?: true
    )
}
