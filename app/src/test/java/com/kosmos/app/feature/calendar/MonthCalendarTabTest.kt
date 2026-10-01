package com.kosmos.app.feature.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.MonthSchedule
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import com.kosmos.app.ui.theme.KosmosTheme
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * [MonthCalendarTabTest]
 * 월 탭의 날짜 선택 → 그날 일정 표시, 화살표로 달 이동을 고정합니다 (0.28.0 M4).
 * [WHY] Robolectric 기본 화면은 작아 그리드 아래 목록이 화면 밖이다 — 목록 단언은 performScrollTo 뒤에 한다.
 * [WHY] 화면이 아니라 탭 컴포저블을 직접 띄운다 — CalendarScreen 은 진입 시 캘린더 권한 런처를 띄워 테스트와 무관한 경로를 탄다.
 */
@RunWith(RobolectricTestRunner::class)
class MonthCalendarTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val utc = ZoneId.of("UTC")
    private val october = YearMonth.of(2026, 10)
    private val useCase: GetTodayScheduleUseCase = mockk {
        coEvery { month(october, any()) } returns AppResult.Success(
            MonthSchedule(
                october,
                persistentListOf(
                    CalendarEvent("a", "치과 예약", "2026-10-02T15:00:00", "2026-10-02T16:00:00"),
                    CalendarEvent("d", "팀 회식", "2026-10-02T19:00:00", "2026-10-02T21:00:00", source = CalendarEvent.Source.DEVICE)
                )
            )
        )
        coEvery { month(YearMonth.of(2026, 11), any()) } returns AppResult.Success(MonthSchedule(YearMonth.of(2026, 11), persistentListOf()))
    }
    private val viewModel = CalendarViewModel(useCase, mockk(relaxed = true))

    private fun show() {
        viewModel.showMonth(october, zoneId = utc)
        composeRule.setContent {
            KosmosTheme { MonthCalendarTab(viewModel = viewModel, today = LocalDate.of(2026, 10, 1), zoneId = utc) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `날짜를 누르면 그날 일정이 출처와 함께 나온다`() {
        show()

        composeRule.onNodeWithTag("month_cell_2026-10-02").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("10월 2일 금요일").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("치과 예약").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("팀 회식").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `일정 없는 날은 빈 안내가 나온다`() {
        show()

        composeRule.onNodeWithTag("month_cell_2026-10-05").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("일정이 없어요.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `다음 달 화살표로 달이 바뀐다`() {
        show()

        composeRule.onNodeWithContentDescription("다음 달").performClick()
        composeRule.waitForIdle()

        assertEquals(YearMonth.of(2026, 11), viewModel.visibleMonth.value)
        composeRule.onNodeWithText("2026년 11월").assertIsDisplayed()
    }
}
