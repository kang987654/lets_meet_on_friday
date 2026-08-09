package com.kosmos.app.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * @property deviceCalendarFailed 기기 시스템 캘린더를 읽지 못했는지 여부입니다.
 *   [WHY] 예전에는 읽기 실패를 `emptyList()` 로 조용히 삼켰다. 그러면 화면에는 앱 안의 일정만
 *   나오는데 사용자는 **기기 일정이 없다고 믿는다** — 틀린 답을 성공처럼 보여 주는 셈이다.
 *   권한 거부와 Provider 오류 모두 여기로 올라온다 (PRD EC4·EC2).
 */
data class ScheduleData(
    val events: ImmutableList<CalendarEvent>,
    val summary: String?,
    val rangeType: RangeType,
    val deviceCalendarFailed: Boolean = false
) {
    enum class RangeType {
        TODAY,
        WEEK
    }
}
