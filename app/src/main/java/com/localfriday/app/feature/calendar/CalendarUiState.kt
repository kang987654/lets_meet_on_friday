package com.localfriday.app.feature.calendar

import com.localfriday.app.domain.model.ScheduleData

sealed class CalendarUiState {
    object Idle : CalendarUiState()
    object Loading : CalendarUiState()
    data class Success(val scheduleData: ScheduleData) : CalendarUiState()
    object Empty : CalendarUiState()
    data class Error(val message: String) : CalendarUiState()
    object PermissionRequired : CalendarUiState()
}
