package com.kosmos.app.feature.calendar

import com.kosmos.app.core.common.AppError
import com.kosmos.app.domain.model.ScheduleData

sealed class CalendarUiState {
    object Idle : CalendarUiState()
    object Loading : CalendarUiState()
    data class Success(val scheduleData: ScheduleData) : CalendarUiState()
    object Empty : CalendarUiState()
    data class Error(val error: AppError) : CalendarUiState()
}
