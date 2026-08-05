package com.kosmos.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import com.kosmos.app.domain.util.IsoDateTimeParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * [CalendarViewModel]
 * 캘린더 화면의 일정 조회 범위(오늘/이번 주)와 날짜 선택 필터를 관리하는 뷰모델입니다.
 *
 * ### Architecture Context
 * - **Layer**: Feature / Presentation (Calendar)
 * - **Dependencies**: [GetTodayScheduleUseCase], [IsoDateTimeParser]
 *
 * ### Key Flow
 * 1. [loadSchedule]이 선택된 범위의 일정을 조회해 내부 상태에 보관합니다(로컬 DB + 기기 캘린더 병합, ADR-004).
 * 2. [onDateSelected]로 특정 날짜를 고르면 해당 일자만 남기고, 같은 날짜를 다시 누르면 필터가 해제됩니다.
 * 3. [uiState]는 조회 결과와 날짜 필터를 합성해 화면에 노출할 최종 상태를 만듭니다.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getTodayScheduleUseCase: GetTodayScheduleUseCase
) : ViewModel() {

    private val _loadedState = MutableStateFlow<CalendarUiState>(CalendarUiState.Idle)

    private val _selectedRange = MutableStateFlow(ScheduleData.RangeType.TODAY)
    val selectedRange: StateFlow<ScheduleData.RangeType> = _selectedRange.asStateFlow()

    /** null이면 조회 범위 전체를 표시합니다. */
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    // [WHY] 날짜 필터는 재조회 없이 즉시 반영되어야 하므로, 조회 결과와 필터를 합성해 파생 상태로 만든다.
    val uiState: StateFlow<CalendarUiState> = combine(_loadedState, _selectedDate) { loaded, date ->
        applyDateFilter(loaded, date)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState.Idle
    )

    fun loadSchedule(rangeType: ScheduleData.RangeType = _selectedRange.value) {
        viewModelScope.launch {
            _selectedRange.value = rangeType
            _loadedState.value = CalendarUiState.Loading

            _loadedState.value = when (val result = getTodayScheduleUseCase(rangeType)) {
                is AppResult.Success ->
                    if (result.data.events.isEmpty()) CalendarUiState.Empty
                    else CalendarUiState.Success(result.data)
                is AppResult.Failure -> CalendarUiState.Error(result.error)
            }
        }
    }

    /** 조회 범위를 변경하고 즉시 재조회합니다. 범위가 바뀌면 날짜 필터는 초기화합니다. */
    fun onRangeSelected(rangeType: ScheduleData.RangeType) {
        if (_selectedRange.value == rangeType) return
        _selectedDate.value = null
        loadSchedule(rangeType)
    }

    /**
     * 날짜 스트립에서 날짜를 선택합니다. 같은 날짜를 다시 누르면 필터가 해제됩니다.
     * [WHY] 오늘이 아닌 날짜는 TODAY 범위 데이터에 존재하지 않으므로 주간 범위로 자동 확장해
     * "탭했는데 아무것도 없는" 상황을 막는다.
     */
    fun onDateSelected(date: LocalDate) {
        if (_selectedDate.value == date) {
            _selectedDate.value = null
            return
        }
        _selectedDate.value = date
        if (date != LocalDate.now() && _selectedRange.value != ScheduleData.RangeType.WEEK) {
            loadSchedule(ScheduleData.RangeType.WEEK)
        }
    }

    private fun applyDateFilter(state: CalendarUiState, date: LocalDate?): CalendarUiState {
        if (date == null || state !is CalendarUiState.Success) return state

        val filtered = state.scheduleData.events.filter { event ->
            IsoDateTimeParser.toLocalDate(event.startIso) == date
        }
        return if (filtered.isEmpty()) {
            CalendarUiState.Empty
        } else {
            CalendarUiState.Success(state.scheduleData.copy(events = filtered.toImmutableList()))
        }
    }
}
