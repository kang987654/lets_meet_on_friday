package com.kosmos.app.feature.memory

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [MemoryViewModelTest]
 * 지식 삭제(C′2 관리 장치)가 저장소를 부르고, 실패는 기존 actionError 통로로 흐르는지 고정합니다.
 *
 * [WHY] `KnowledgeRepository.delete` 는 이 회차 전까지 호출처 0곳이었다 — 자동 저장을 되돌릴
 * 수 있어야 G3 예외(지식 자동 저장)가 성립한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val knowledgeRepository: KnowledgeRepository = mockk()
    private val taskRepository: TaskRepository = mockk(relaxed = true)
    private val scheduler: com.kosmos.app.platform.alarm.ReminderAlarmScheduler = mockk(relaxed = true)

    private fun viewModel() = MemoryViewModel(
        knowledgeRepository = knowledgeRepository,
        taskRepository = taskRepository,
        exportMemoryUseCase = mockk(relaxed = true),
        importMemoryUseCase = mockk(relaxed = true),
        backupFileWriter = mockk(relaxed = true),
        reminderAlarmScheduler = scheduler,
        widgetRefresher = mockk(relaxed = true)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `지식 삭제 성공은 저장소를 부르고 onDeleted 를 알린다`() = runTest(dispatcher.scheduler) {
        coEvery { knowledgeRepository.delete("k1") } returns AppResult.Success(Unit)
        var deleted = false

        val vm = viewModel()
        vm.deleteKnowledge("k1") { deleted = true }
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { knowledgeRepository.delete("k1") }
        assertTrue(deleted)
        assertNull(vm.uiState.value.actionError)
    }

    @Test
    fun `지식 삭제 실패는 actionError 로 알린다`() = runTest(dispatcher.scheduler) {
        coEvery { knowledgeRepository.delete("k1") } returns AppResult.Failure(AppError.DbWriteError("knowledge_note"))
        var deleted = false

        val vm = viewModel()
        vm.deleteKnowledge("k1") { deleted = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(!deleted)
        assertNotNull(vm.uiState.value.actionError)
    }

    private val now = 1_800_000_000_000L
    private fun reminderTask(remindAtMs: Long, remindedAtMs: Long? = null) = com.kosmos.app.domain.model.TaskItem(
        id = "t1", title = "약 먹기", isCompleted = true, createdAt = 1,
        remindAtIso = java.time.Instant.ofEpochMilli(remindAtMs).toString(), remindedAtMs = remindedAtMs
    )

    private fun stubRestore() {
        coEvery { taskRepository.updateCompletion("t1", false) } returns AppResult.Success(Unit)
        coEvery { taskRepository.markReminded(any(), any()) } returns AppResult.Success(Unit)
        coEvery { taskRepository.getCounts() } returns AppResult.Success(com.kosmos.app.domain.memory.TaskCounts(1, 0))
    }

    @Test
    fun `되살린 할 일의 알림 시각이 미래면 알람을 다시 예약한다`() = runTest(dispatcher.scheduler) {
        stubRestore()
        var done = false

        viewModel().restoreTask(reminderTask(now + 60_000), now) { done = true }
        dispatcher.scheduler.advanceUntilIdle()

        io.mockk.verify(exactly = 1) { scheduler.schedule("t1", now + 60_000) }
        coVerify(exactly = 0) { taskRepository.markReminded(any(), any()) }
        assertTrue(done)
    }

    @Test
    fun `되살린 할 일의 알림 시각이 지났으면 울리지 않고 발화 처리만 한다`() = runTest(dispatcher.scheduler) {
        stubRestore()

        viewModel().restoreTask(reminderTask(now - 60_000), now)
        dispatcher.scheduler.advanceUntilIdle()

        io.mockk.verify(exactly = 0) { scheduler.schedule(any(), any()) }
        coVerify(exactly = 1) { taskRepository.markReminded("t1", now) }
    }

    @Test
    fun `이미 울린 리마인더는 되살려도 다시 예약하지 않는다`() = runTest(dispatcher.scheduler) {
        stubRestore()

        viewModel().restoreTask(reminderTask(now + 60_000, remindedAtMs = now - 1), now)
        dispatcher.scheduler.advanceUntilIdle()

        io.mockk.verify(exactly = 0) { scheduler.schedule(any(), any()) }
        coVerify(exactly = 0) { taskRepository.markReminded(any(), any()) }
    }

    @Test
    fun `할 일 삭제는 알람을 취소하고 실패는 actionError 로 알린다`() = runTest(dispatcher.scheduler) {
        coEvery { taskRepository.delete("t1") } returns AppResult.Success(Unit)
        coEvery { taskRepository.getCounts() } returns AppResult.Success(com.kosmos.app.domain.memory.TaskCounts(0, 0))
        val vm = viewModel()

        vm.deleteTask("t1")
        dispatcher.scheduler.advanceUntilIdle()
        io.mockk.verify(exactly = 1) { scheduler.cancel("t1") }

        coEvery { taskRepository.delete("t2") } returns AppResult.Failure(AppError.DbWriteError("task_item"))
        vm.deleteTask("t2")
        dispatcher.scheduler.advanceUntilIdle()
        io.mockk.verify(exactly = 0) { scheduler.cancel("t2") }
        assertNotNull(vm.uiState.value.actionError)
    }

    // --- 지운 항목 되돌리기 (0.38.3 M2) ---

    @Test
    fun `지운 할 일을 되돌리면 같은 항목을 저장하고 미래 알림은 다시 예약한다`() = runTest(dispatcher.scheduler) {
        val task = reminderTask(now + 60_000).copy(isCompleted = false)
        coEvery { taskRepository.save(task) } returns AppResult.Success(Unit)
        coEvery { taskRepository.getCounts() } returns AppResult.Success(com.kosmos.app.domain.memory.TaskCounts(1, 0))
        var done = false

        viewModel().undeleteTask(task, now) { done = true }
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { taskRepository.save(task) }
        io.mockk.verify(exactly = 1) { scheduler.schedule("t1", now + 60_000) }
        assertTrue(done)
    }

    @Test
    fun `지운 메모를 되돌리면 같은 메모를 저장하고 실패는 actionError 로 알린다`() = runTest(dispatcher.scheduler) {
        val note = com.kosmos.app.domain.model.KnowledgeNote(id = "k1", content = "자전거 비밀번호는 134", createdAt = 1, updatedAt = 1)
        coEvery { knowledgeRepository.save(note) } returns AppResult.Success(Unit)
        val vm = viewModel()
        var done = false

        vm.undeleteKnowledge(note) { done = true }
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(done)

        coEvery { knowledgeRepository.save(note) } returns AppResult.Failure(AppError.DbWriteError("knowledge_note"))
        vm.undeleteKnowledge(note)
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.actionError)
    }
}
