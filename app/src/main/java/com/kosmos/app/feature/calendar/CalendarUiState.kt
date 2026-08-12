package com.kosmos.app.feature.calendar

import com.kosmos.app.core.common.AppError
import com.kosmos.app.domain.model.ScheduleData

/**
 * [WHY] 예전에는 `Empty` 가 따로 있었다. 그런데 그것은 `Success(scheduleData).events.isEmpty()`
 * 와 **같은 것을 두 번 표현**한 것이었고, 그 이중 표현이 데이터를 버리는 경로를 만들었다 —
 * 일정이 0건이면 뷰모델이 `ScheduleData` 를 통째로 버리고 `Empty` 를 내보내
 * `deviceCalendarFailed` 가 소실됐다. 그래서 **가장 필요한 상황**(앱 일정도 없고 기기 캘린더도
 * 못 읽을 때)에서만 안내 카드가 사라졌다(2026-08-12 실기기, PRD EC4 위반).
 *
 * 표현을 하나로 줄여 그 경로 자체를 없앤다. "비었다"는 판단은 화면이 `events` 를 보고 한다.
 */
sealed class CalendarUiState {
    object Idle : CalendarUiState()
    object Loading : CalendarUiState()
    data class Success(val scheduleData: ScheduleData) : CalendarUiState()
    data class Error(val error: AppError) : CalendarUiState()
}
