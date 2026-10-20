package com.kosmos.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * [CalendarViewModel]
 * 일정 화면의 탭(오늘/월), "오늘" 탭의 일정·알림·할 일 조회와 할 일 완료·알림 수정, 월 그리드 조회를 맡는 뷰모델입니다.
 *
 * ### Architecture Context
 * - **Layer**: Feature / Presentation (Calendar)
 * - **Dependencies**: [GetTodayScheduleUseCase], TaskRepository, ReminderEditor(0.38.2 수정 시트), WidgetRefresher
 *
 * ### Key Flow
 * 1. [loadSchedule]이 오늘 포함 7일의 일정(로컬 DB + 기기 캘린더, ADR-004)과 할 일·알림을 읽는다 — 묶음은 화면이 [TodayComposer] 로 만든다.
 * 2. 할 일 체크 원은 [completeTask], 알림 줄은 [openReminder] → 0.38.2 수정 시트(미루기·시간 바꾸기·취소).
 * 3. 월 탭은 [showMonth]·[selectMonthDate].
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getTodayScheduleUseCase: GetTodayScheduleUseCase,
    private val taskRepository: com.kosmos.app.domain.memory.TaskRepository,
    private val reminderEditor: com.kosmos.app.feature.chat.result.ReminderEditor,
    private val widgetRefresher: com.kosmos.app.widget.WidgetRefresher
) : ViewModel() {

    private val _loadedState = MutableStateFlow<CalendarUiState>(CalendarUiState.Idle)

    // [WHY] 첫 탭이자 기본 탭은 "오늘"(0.38.3 M1 사용자 결정 — 0.28.0 의 "월 기본"을 바꿨다).
    private val _tab = MutableStateFlow(CalendarTab.TODAY)
    val tab: StateFlow<CalendarTab> = _tab.asStateFlow()

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    private val _monthState = MutableStateFlow<MonthUiState>(MonthUiState.Loading)
    val monthState: StateFlow<MonthUiState> = _monthState.asStateFlow()

    private val _monthSelectedDate = MutableStateFlow<LocalDate?>(LocalDate.now())
    val monthSelectedDate: StateFlow<LocalDate?> = _monthSelectedDate.asStateFlow()

    private var monthJob: kotlinx.coroutines.Job? = null

    /** "오늘" 탭의 일정 조회 결과(오늘 포함 7일 — 오늘 묶음과 다가오는 일정이 나눠 쓴다). */
    val uiState: StateFlow<CalendarUiState> = _loadedState.asStateFlow()

    private val _reminders = MutableStateFlow<List<com.kosmos.app.domain.model.TaskItem>>(emptyList())
    /** 아직 울리지 않은 알림 — 오늘 것만 화면이 고른다([TodayComposer]). */
    val reminders: StateFlow<List<com.kosmos.app.domain.model.TaskItem>> = _reminders.asStateFlow()

    private val _pendingTasks = MutableStateFlow<List<com.kosmos.app.domain.model.TaskItem>>(emptyList())
    val pendingTasks: StateFlow<List<com.kosmos.app.domain.model.TaskItem>> = _pendingTasks.asStateFlow()

    /**
     * "오늘" 탭을 채운다 — 일정(오늘 포함 7일)과 할 일·알림을 함께 읽는다.
     *
     * [WHY] 일정이 0건이어도 `Success` 를 낸다. 예전에는 `Empty` 를 내면서 `ScheduleData` 를 버려 `deviceCalendarFailed` 가 사라졌다 —
     * 기기 캘린더를 못 읽는데 앱 일정마저 없을 때, 즉 안내가 가장 필요한 순간에만 안내가 없어졌다.
     * "이번 주 요약"(AI 요약)은 0.38.3 M1 에서 뺐다 — 채팅 아침 브리핑과 겹친다(사용자 결정).
     *
     * @param now 화면 기준 시각(카탈로그 고정 시계) — null 이면 유스케이스가 벽시계를 읽는다.
     */
    fun loadSchedule(
        rangeType: ScheduleData.RangeType = ScheduleData.RangeType.WEEK,
        now: Long? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        viewModelScope.launch {
            _loadedState.value = CalendarUiState.Loading
            val result = if (now == null) getTodayScheduleUseCase(rangeType) else getTodayScheduleUseCase(rangeType, now, zoneId)
            _loadedState.value = when (result) {
                is AppResult.Success -> CalendarUiState.Success(result.data)
                is AppResult.Failure -> CalendarUiState.Error(result.error)
            }
        }
        loadTasks()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            (taskRepository.getActiveReminders() as? AppResult.Success)?.let { _reminders.value = it.data }
            (taskRepository.getPendingTasksData(0, TODAY_TASK_LIMIT) as? AppResult.Success)?.let { _pendingTasks.value = it.data }
        }
    }

    /** 오늘 탭의 체크 원 — 할 일을 끝낸다(위젯의 남은 할 일 수도 갱신). */
    fun completeTask(taskId: String) {
        viewModelScope.launch {
            if (taskRepository.updateCompletion(taskId, true) is AppResult.Success) {
                widgetRefresher.refresh()
                loadTasks()
            }
        }
    }

    // ── 알림 수정 시트(0.38.2 수정 시트 재사용) ──

    private val _editingReminder = MutableStateFlow<String?>(null)
    val editingReminder: StateFlow<String?> = _editingReminder.asStateFlow()

    private val _editError = MutableStateFlow<String?>(null)
    val editError: StateFlow<String?> = _editError.asStateFlow()

    fun openReminder(taskId: String) {
        _editError.value = null
        _editingReminder.value = taskId
    }

    fun closeReminder() {
        _editingReminder.value = null
        _editError.value = null
    }

    fun snooze(taskId: String) = editReminder { reminderEditor.snooze(taskId) }
    fun reschedule(taskId: String, hour: Int, minute: Int) = editReminder { reminderEditor.reschedule(taskId, hour, minute) }
    fun cancelReminder(taskId: String) = editReminder { reminderEditor.cancel(taskId) }

    private fun editReminder(action: suspend () -> AppResult<com.kosmos.app.domain.model.ChatMessage>) {
        viewModelScope.launch {
            when (val result = action()) {
                is AppResult.Success -> { closeReminder(); loadTasks() }
                is AppResult.Failure -> _editError.value =
                    (result.error as? com.kosmos.app.core.common.AppError.ValidationError)?.reason ?: "알림을 바꾸지 못했어요. 다시 시도해 주세요."
            }
        }
    }

    /** 상단 탭을 바꿉니다. */
    fun onTabSelected(tab: CalendarTab) {
        _tab.value = tab
    }

    /**
     * [yearMonth] 를 보여 주고 그 달을 조회합니다. 같은 달이 이미 성공 상태면 다시 읽지 않는다 — [force] 는
     * 권한 승인 직후처럼 데이터가 바뀌었을 때만.
     *
     * [WHY] 이전 달 조회가 늦게 끝나 새 달 상태를 덮지 않도록 진행 중인 조회를 취소한다(빠른 스와이프).
     */
    fun showMonth(yearMonth: YearMonth, force: Boolean = false, zoneId: ZoneId = ZoneId.systemDefault()) {
        val current = _monthState.value
        if (!force && yearMonth == _visibleMonth.value &&
            current is MonthUiState.Success && current.schedule.month == yearMonth
        ) return
        if (yearMonth != _visibleMonth.value) _monthSelectedDate.value = null
        _visibleMonth.value = yearMonth
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            _monthState.value = MonthUiState.Loading
            _monthState.value = when (val result = getTodayScheduleUseCase.month(yearMonth, zoneId)) {
                is AppResult.Success -> MonthUiState.Success(result.data)
                is AppResult.Failure -> MonthUiState.Error(result.error)
            }
        }
    }

    /** 월 그리드에서 날짜를 고릅니다. 다른 달의 날짜(앞뒤 채움 칸)면 그 달로 넘어간다. */
    fun selectMonthDate(date: LocalDate) {
        val month = YearMonth.from(date)
        if (month != _visibleMonth.value) showMonth(month)
        _monthSelectedDate.value = date
    }

    private companion object {
        /** 오늘 탭 할 일 목록 상한 — 개인 1인 규모라 한 번에 읽는다. */
        const val TODAY_TASK_LIMIT = 100
    }
}
