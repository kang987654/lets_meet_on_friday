package com.localfriday.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.model.ScheduleData
import com.localfriday.app.domain.usecase.GetTodayScheduleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getTodayScheduleUseCase: GetTodayScheduleUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Idle)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _selectedRange = MutableStateFlow(ScheduleData.RangeType.TODAY)
    val selectedRange: StateFlow<ScheduleData.RangeType> = _selectedRange.asStateFlow()

    fun loadSchedule(rangeType: ScheduleData.RangeType = _selectedRange.value) {
        viewModelScope.launch {
            _selectedRange.value = rangeType
            _uiState.value = CalendarUiState.Loading

            when (val result = getTodayScheduleUseCase(rangeType)) {
                is AppResult.Success -> {
                    if (result.data.events.isEmpty()) {
                        _uiState.value = CalendarUiState.Empty
                    } else {
                        _uiState.value = CalendarUiState.Success(result.data)
                    }
                }
                is AppResult.Failure -> {
                    _uiState.value = CalendarUiState.Error(result.error)
                }
            }
        }
    }
}
