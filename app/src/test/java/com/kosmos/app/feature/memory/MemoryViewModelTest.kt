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

    private fun viewModel() = MemoryViewModel(
        knowledgeRepository = knowledgeRepository,
        taskRepository = taskRepository,
        exportMemoryUseCase = mockk(relaxed = true),
        importMemoryUseCase = mockk(relaxed = true),
        backupFileWriter = mockk(relaxed = true),
        reminderAlarmScheduler = mockk(relaxed = true),
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
}
