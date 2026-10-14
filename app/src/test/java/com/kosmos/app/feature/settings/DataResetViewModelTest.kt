package com.kosmos.app.feature.settings

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.memory.DataResetRepository
import com.kosmos.app.domain.memory.ResetTarget
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.platform.alarm.ReminderAlarmScheduler
import com.kosmos.app.widget.WidgetRefresher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [DataResetViewModelTest]
 * 데이터 초기화(0.37.1)의 조율 — 알람은 **지우기 전에** 취소, 대화 기록이면 세션을 지우고 재시작 요구, 실패는 안내.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataResetViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository: DataResetRepository = mockk()
    private val tasks: TaskRepository = mockk()
    private val scheduler: ReminderAlarmScheduler = mockk(relaxed = true)
    private val widget: WidgetRefresher = mockk(relaxed = true)
    private val session: SessionStore = mockk(relaxed = true)

    private fun viewModel() = DataResetViewModel(repository, tasks, scheduler, widget, session)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.reset(any()) } returns AppResult.Success(Unit)
        coEvery { tasks.getActiveReminders() } returns AppResult.Success(
            listOf(TaskItem(id = "t1", title = "약", isCompleted = false, createdAt = 1, remindAtIso = "2026-10-15T09:00:00"))
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `할 일을 지우면 지우기 전에 알람을 취소하고 위젯을 갱신하며 재시작은 필요 없다`() = runTest(dispatcher.scheduler) {
        val vm = viewModel()

        vm.reset(setOf(ResetTarget.TASKS))
        dispatcher.scheduler.advanceUntilIdle()

        coVerifyOrder {
            scheduler.cancel("t1")
            repository.reset(setOf(ResetTarget.TASKS))
            widget.refresh()
        }
        coVerify(exactly = 0) { session.clearActiveSessionId() }
        assertEquals(DataResetState.Done(needsRestart = false), vm.state.value)
    }

    @Test
    fun `대화 기록을 지우면 세션을 지우고 재시작을 요구하며 닫을 수 없다`() = runTest(dispatcher.scheduler) {
        val vm = viewModel()

        vm.reset(setOf(ResetTarget.CONVERSATIONS, ResetTarget.KNOWLEDGE))
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { session.clearActiveSessionId() }
        verify(exactly = 0) { scheduler.cancel(any()) }
        assertEquals(DataResetState.Done(needsRestart = true), vm.state.value)
        vm.dismiss()
        assertEquals(DataResetState.Done(needsRestart = true), vm.state.value)
    }

    @Test
    fun `실패는 안내하고 고른 것이 없으면 아무것도 하지 않는다`() = runTest(dispatcher.scheduler) {
        val vm = viewModel()
        vm.reset(emptySet())
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { repository.reset(any()) }
        assertEquals(DataResetState.Idle, vm.state.value)

        coEvery { repository.reset(any()) } returns AppResult.Failure(AppError.DbWriteError("reset"))
        vm.reset(setOf(ResetTarget.PROFILE))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value is DataResetState.Failed)
        coVerify(exactly = 0) { session.clearActiveSessionId() }
        vm.dismiss()
        assertEquals(DataResetState.Idle, vm.state.value)
    }
}
