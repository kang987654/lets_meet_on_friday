package com.kosmos.app.domain

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.usecase.AddReminderUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AddReminderUseCaseTest]
 * 리마인더 등록 유스케이스의 검증·저장 경로를 고정합니다.
 *
 * [WHY] now 를 극단값(0 / MAX_VALUE)으로 주입해 시간대 의존 없이 미래/과거 판정을 고정한다 —
 * ISO 문자열은 시간대에 따라 다른 epoch 로 파싱되지만 극단값과의 대소는 불변이다.
 */
class AddReminderUseCaseTest {

    private val repository: TaskRepository = mockk()
    private val useCase = AddReminderUseCase(repository)

    @Test
    fun `미래 시각이면 remindAtIso 가 채워진 TaskItem 을 저장한다`() = runTest {
        val saved = slot<TaskItem>()
        coEvery { repository.save(capture(saved)) } returns AppResult.Success(Unit)

        val result = useCase(content = "약 먹기", remindAtIso = "2026-08-29T15:00:00", now = 0L)

        assertTrue(result is AppResult.Success)
        assertEquals("약 먹기", saved.captured.title)
        assertEquals("2026-08-29T15:00:00", saved.captured.remindAtIso)
        assertFalse(saved.captured.isCompleted)
        assertEquals(0L, saved.captured.createdAt)
        assertEquals(saved.captured, (result as AppResult.Success).data)
    }

    @Test
    fun `ISO 형식이 아니면 ValidationError 로 실패하고 저장하지 않는다`() = runTest {
        val result = useCase(content = "약 먹기", remindAtIso = "내일 3시쯤", now = 0L)

        assertTrue(result is AppResult.Failure)
        assertEquals("time", ((result as AppResult.Failure).error as AppError.ValidationError).field)
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `과거 시각이면 ValidationError 로 실패하고 저장하지 않는다`() = runTest {
        val result = useCase(content = "약 먹기", remindAtIso = "2026-08-29T15:00:00", now = Long.MAX_VALUE)

        assertTrue(result is AppResult.Failure)
        assertEquals("time", ((result as AppResult.Failure).error as AppError.ValidationError).field)
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `저장 실패는 그대로 전파된다`() = runTest {
        coEvery { repository.save(any()) } returns AppResult.Failure(AppError.DbWriteError("task_item"))

        val result = useCase(content = "약 먹기", remindAtIso = "2026-08-29T15:00:00", now = 0L)

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.DbWriteError("task_item"), (result as AppResult.Failure).error)
    }
}
