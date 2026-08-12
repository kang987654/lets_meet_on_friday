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
    private val summarize: com.kosmos.app.domain.usecase.SummarizeScheduleUseCase = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // 기본은 "요약 없음" — 요약을 보는 테스트만 따로 덮어쓴다.
        coEvery { summarize(any(), any()) } returns AppResult.Success("")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `일정이 0건이어도 기기 캘린더 실패 플래그가 유지된다`() = runTest {
        givenSchedule(events = persistentListOf(), deviceCalendarFailed = true)
        val viewModel = CalendarViewModel(useCase, summarize)
        val states = collect(viewModel)

        viewModel.loadSchedule()

        val success = states.filterIsInstance<CalendarUiState.Success>().last()
        assertTrue("일정이 0건이면 events 는 비어 있다", success.scheduleData.events.isEmpty())
        assertTrue(
            "여기서 플래그가 죽으면 안내 카드가 절대 안 뜬다",
            success.scheduleData.deviceCalendarFailed
        )
    }

    @Test
    fun `날짜 필터가 0건을 만들어도 플래그가 유지된다`() = runTest {
        // [WHY] applyDateFilter 에도 같은 붕괴가 있었다 — 필터 결과가 비면 Empty 로 떨어뜨려
        // scheduleData 를 버렸다. 다른 날짜를 골라 본 것만으로 안내가 사라지는 셈이다.
        givenSchedule(
            events = listOf(event("2026-08-12T20:00")).toImmutableList(),
            deviceCalendarFailed = true
        )
        val viewModel = CalendarViewModel(useCase, summarize)
        val states = collect(viewModel)

        viewModel.loadSchedule()
        viewModel.onDateSelected(LocalDate.of(2026, 8, 15)) // 일정이 없는 날

        val success = states.filterIsInstance<CalendarUiState.Success>().last()
        assertTrue(success.scheduleData.events.isEmpty())
        assertTrue(success.scheduleData.deviceCalendarFailed)
    }

    @Test
    fun `날짜 필터는 해당 일자만 남긴다`() = runTest {
        givenSchedule(
            events = listOf(event("2026-08-12T20:00"), event("2026-08-15T09:00")).toImmutableList(),
            deviceCalendarFailed = false
        )
        val viewModel = CalendarViewModel(useCase, summarize)
        val states = collect(viewModel)

        viewModel.loadSchedule()
        viewModel.onDateSelected(LocalDate.of(2026, 8, 15))

        val success = states.filterIsInstance<CalendarUiState.Success>().last()
        assertEquals(1, success.scheduleData.events.size)
        assertEquals("2026-08-15T09:00", success.scheduleData.events.single().startIso)
    }

    @Test
    fun `요약을 기다리지 않고 목록을 먼저 내보낸다`() = runTest {
        // [WHY] 예전에는 요약 추론(~10초)이 끝난 뒤에야 목록이 나왔다. 게다가 화면은 그 요약을
        // 읽지도 않았으니, 매번 결과를 버릴 추론을 사용자가 기다린 셈이다(2026-08-12 실기기).
        givenSchedule(
            events = listOf(event("2026-08-12T20:00")).toImmutableList(),
            deviceCalendarFailed = false
        )
        val gate = kotlinx.coroutines.CompletableDeferred<AppResult<String>>()
        io.mockk.coEvery { summarize(any(), any()) } coAnswers { gate.await() }

        val viewModel = CalendarViewModel(useCase, summarize)
        val states = collect(viewModel)

        viewModel.loadSchedule()

        val beforeSummary = states.filterIsInstance<CalendarUiState.Success>().last()
        assertEquals("요약이 오기 전에 이미 목록이 있어야 한다", 1, beforeSummary.scheduleData.events.size)
        org.junit.Assert.assertNull("아직 요약은 없다", beforeSummary.scheduleData.summary)

        gate.complete(AppResult.Success(SUMMARY))

        val afterSummary = states.filterIsInstance<CalendarUiState.Success>().last()
        assertEquals(SUMMARY, afterSummary.scheduleData.summary)
        assertEquals("요약이 붙어도 목록은 그대로다", 1, afterSummary.scheduleData.events.size)
    }

    @Test
    fun `일정이 없으면 요약을 시도하지 않는다`() = runTest {
        givenSchedule(events = persistentListOf(), deviceCalendarFailed = false)
        val viewModel = CalendarViewModel(useCase, summarize)
        collect(viewModel)

        viewModel.loadSchedule()

        io.mockk.coVerify(exactly = 0) { summarize(any(), any()) }
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

    private companion object {
        const val SUMMARY = "오늘은 저녁 약속이 하나 있습니다."
    }

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
