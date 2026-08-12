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
    private val getTodayScheduleUseCase: GetTodayScheduleUseCase,
    private val summarizeScheduleUseCase: com.kosmos.app.domain.usecase.SummarizeScheduleUseCase
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

    /**
     * 일정을 조회해 **먼저 목록을 내보내고**, AI 요약은 뒤이어 도착하면 덧붙입니다.
     *
     * [WHY] 예전에는 요약 추론(~10초)이 끝날 때까지 목록을 내보내지 않아 캘린더 화면이 그 시간
     * 내내 스피너였다. 게다가 그 요약을 화면이 **읽지도 않았다** — 매번 결과를 버릴 추론을
     * 기다린 셈이다(2026-08-12 실기기). 조회와 요약을 두 단계로 나누면 목록은 즉시 뜨고 요약은
     * 준비되는 대로 채워진다 (PRD F4).
     *
     * [WHY] 요약 실패는 화면에 알리지 않는다. 요약은 목록의 보조 정보이고, 실패했다고 일정
     * 자체를 못 보게 만들 이유가 없다 — 자리를 비워 두는 것이 정확한 표현이다.
     */
    fun loadSchedule(rangeType: ScheduleData.RangeType = _selectedRange.value) {
        viewModelScope.launch {
            _selectedRange.value = rangeType
            _loadedState.value = CalendarUiState.Loading

            // [WHY] 일정이 0건이어도 `Success` 를 낸다. 예전에는 `Empty` 를 내면서 `ScheduleData`
            // 를 버려 `deviceCalendarFailed` 가 사라졌다 — 기기 캘린더를 못 읽는데 앱 일정마저
            // 없을 때, 즉 안내가 가장 필요한 순간에만 안내가 없어졌다.
            val loaded = when (val result = getTodayScheduleUseCase(rangeType)) {
                is AppResult.Success -> CalendarUiState.Success(result.data)
                is AppResult.Failure -> CalendarUiState.Error(result.error)
            }
            _loadedState.value = loaded

            if (loaded is CalendarUiState.Success && loaded.scheduleData.events.isNotEmpty()) {
                val summary = summarizeScheduleUseCase(loaded.scheduleData.events, rangeType)
                if (summary is AppResult.Success && summary.data.isNotBlank()) {
                    // [WHY] 요약이 도착하는 동안 사용자가 범위를 바꿨을 수 있다. 그때 낡은 요약을
                    // 덮어쓰면 다른 범위의 요약이 붙으므로, 현재 상태가 그대로일 때만 갱신한다.
                    val current = _loadedState.value
                    if (current === loaded) {
                        _loadedState.value =
                            CalendarUiState.Success(loaded.scheduleData.copy(summary = summary.data))
                    }
                }
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
        // [WHY] 결과가 비어도 `scheduleData` 를 유지한다. 예전에는 `Empty` 로 떨어뜨려
        // `deviceCalendarFailed` 를 버렸으므로, **일정 없는 날짜를 눌러 본 것만으로** 기기 캘린더
        // 안내가 사라졌다.
        return CalendarUiState.Success(state.scheduleData.copy(events = filtered.toImmutableList()))
    }
}
