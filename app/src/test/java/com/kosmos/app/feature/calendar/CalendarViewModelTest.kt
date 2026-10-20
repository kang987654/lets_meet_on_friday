package com.kosmos.app.feature.calendar

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * [CalendarViewModelTest]
 * 기기 캘린더 실패 플래그가 **상태 전환에서 살아남는지** 검증합니다.
 *
 * [WHY] 2026-08-12 실기기에서 `READ_CALENDAR` 를 거부한 채 일정 탭에 들어갔더니 안내 카드가
 * 뜨지 않고 "일정이 없습니다." 만 나왔다. 원인은 뷰모델이 일정 0건일 때 `CalendarUiState.Empty`
 * 를 내보내며 **`ScheduleData` 를 통째로 버린** 것이다. `Empty` 에는 `scheduleData` 가 없어
 * `deviceCalendarFailed` 가 소실되고, 화면의 `as? Success` 검사가 영원히 false 가 된다.
 *
 * 하필 **가장 필요한 상황**에서만 안내가 사라진다 — 앱 일정도 없고 기기 캘린더도 못 읽을 때다.
 * 사용자는 진짜 일정이 없다고 믿게 된다(PRD EC4 가 막으려던 바로 그 실패).
 *
 * [WHY] 그래서 `Empty` 를 없애고 `Success` 하나로 합쳤다. `Empty` 는 `events.isEmpty()` 와
 * 같은 것을 두 번 표현한 것이고, 그 이중 표현이 데이터를 버리는 경로를 만들었다.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val useCase: GetTodayScheduleUseCase = mockk()
    private val tasks: com.kosmos.app.domain.memory.TaskRepository = mockk(relaxed = true)
    private val widget: com.kosmos.app.widget.WidgetRefresher = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { tasks.getActiveReminders() } returns AppResult.Success(emptyList())
        coEvery { tasks.getPendingTasksData(any(), any()) } returns AppResult.Success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `일정이 0건이어도 기기 캘린더 실패 플래그가 유지된다`() = runTest {
        givenSchedule(events = persistentListOf(), deviceCalendarFailed = true)
        val viewModel = CalendarViewModel(useCase, tasks, mockk(relaxed = true), widget)
        val states = collect(viewModel)

        viewModel.loadSchedule()

        val success = states.filterIsInstance<CalendarUiState.Success>().last()
        assertTrue("일정이 0건이면 events 는 비어 있다", success.scheduleData.events.isEmpty())
        assertTrue(
            "여기서 플래그가 죽으면 안내 카드가 절대 안 뜬다",
            success.scheduleData.deviceCalendarFailed
        )
    }

    // --- 오늘 탭 (0.38.3 M1) ---

    @Test
    fun `오늘 탭을 열면 일정과 함께 알림·남은 할 일을 읽는다`() = runTest {
        givenSchedule(events = persistentListOf(), deviceCalendarFailed = false)
        val reminder = com.kosmos.app.domain.model.TaskItem(id = "r1", title = "물 마시기", isCompleted = false, createdAt = 0, remindAtIso = "2026-10-15T13:56:00")
        val todo = com.kosmos.app.domain.model.TaskItem(id = "t1", title = "장보기", isCompleted = false, createdAt = 0)
        coEvery { tasks.getActiveReminders() } returns AppResult.Success(listOf(reminder))
        coEvery { tasks.getPendingTasksData(any(), any()) } returns AppResult.Success(listOf(reminder, todo))
        val viewModel = CalendarViewModel(useCase, tasks, mockk(relaxed = true), widget)

        viewModel.loadSchedule()

        assertEquals(listOf(reminder), viewModel.reminders.value)
        assertEquals(listOf(reminder, todo), viewModel.pendingTasks.value)
        assertEquals(CalendarTab.TODAY, viewModel.tab.value)
    }

    @Test
    fun `체크 원으로 할 일을 끝내면 저장하고 위젯을 갱신한다`() = runTest {
        coEvery { tasks.updateCompletion("t1", true) } returns AppResult.Success(Unit)
        val viewModel = CalendarViewModel(useCase, tasks, mockk(relaxed = true), widget)

        viewModel.completeTask("t1")

        io.mockk.coVerify { tasks.updateCompletion("t1", true) }
        io.mockk.coVerify { widget.refresh() }
    }

    // --- 월 탭 (0.28.0) ---

    @Test
    fun `월 조회는 그 달을 읽고 요약하지 않는다`() = runTest {
        val october = java.time.YearMonth.of(2026, 10)
        givenMonth(october)
        val viewModel = CalendarViewModel(useCase, tasks, mockk(relaxed = true), widget)

        viewModel.showMonth(october, zoneId = java.time.ZoneId.of("UTC"))

        val state = viewModel.monthState.value as MonthUiState.Success
        assertEquals(october, state.schedule.month)
        io.mockk.coVerify(exactly = 1) { useCase.month(october, any()) }
    }

    @Test
    fun `같은 달을 다시 보이면 재조회하지 않고 force 면 다시 읽는다`() = runTest {
        val october = java.time.YearMonth.of(2026, 10)
        givenMonth(october)
        val viewModel = CalendarViewModel(useCase, tasks, mockk(relaxed = true), widget)

        viewModel.showMonth(october)
        viewModel.showMonth(october)
        io.mockk.coVerify(exactly = 1) { useCase.month(october, any()) }

        viewModel.showMonth(october, force = true)
        io.mockk.coVerify(exactly = 2) { useCase.month(october, any()) }
    }

    @Test
    fun `이웃 달 칸을 고르면 그 달로 넘어가고 날짜가 선택된다`() = runTest {
        val october = java.time.YearMonth.of(2026, 10)
        val september = java.time.YearMonth.of(2026, 9)
        givenMonth(october)
        givenMonth(september)
        val viewModel = CalendarViewModel(useCase, tasks, mockk(relaxed = true), widget)
        viewModel.showMonth(october)

        viewModel.selectMonthDate(LocalDate.of(2026, 9, 30))

        assertEquals(september, viewModel.visibleMonth.value)
        assertEquals(LocalDate.of(2026, 9, 30), viewModel.monthSelectedDate.value)
        io.mockk.coVerify(exactly = 1) { useCase.month(september, any()) }
    }

    private fun givenMonth(month: java.time.YearMonth) {
        coEvery { useCase.month(month, any()) } returns AppResult.Success(
            com.kosmos.app.domain.model.MonthSchedule(month, persistentListOf())
        )
    }

    private fun givenSchedule(
        events: kotlinx.collections.immutable.ImmutableList<CalendarEvent>,
        deviceCalendarFailed: Boolean
    ) {
        coEvery { useCase(any()) } returns AppResult.Success(
            ScheduleData(
                events = events,
                summary = null,
                rangeType = ScheduleData.RangeType.TODAY,
                deviceCalendarFailed = deviceCalendarFailed
            )
        )
    }

    private fun event(startIso: String) = CalendarEvent(
        id = startIso,
        title = "약속",
        startIso = startIso,
        endIso = startIso
    )


    /**
     * `uiState` 는 `WhileSubscribed` 라 구독자가 있어야 흐른다.
     *
     * [WHY] 수집을 `UnconfinedTestDispatcher` 로 띄운다. `backgroundScope` 의 기본
     * `StandardTestDispatcher` 로 띄우면 스케줄러가 진행될 때까지 구독이 등록되지 않아,
     * 그 사이에 `loadSchedule()` 이 낸 상태를 놓친다.
     */
    private fun kotlinx.coroutines.test.TestScope.collect(
        viewModel: CalendarViewModel
    ): List<CalendarUiState> {
        val states = mutableListOf<CalendarUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states += it }
        }
        return states
    }
}
