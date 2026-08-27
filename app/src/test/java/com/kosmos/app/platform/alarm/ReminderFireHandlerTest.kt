package com.kosmos.app.platform.alarm

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.platform.notification.ReminderNotifier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [ReminderFireHandlerTest]
 * 발화 핸들러의 DB 재확인 방어선을 고정합니다 — 알람 취소 누락 경합(완료·기발화 후 알람이
 * 그대로 울리는 경우)에서 알림이 나가지 않아야 한다.
 */
class ReminderFireHandlerTest {

    private val repository: TaskRepository = mockk()
    private val notifier: ReminderNotifier = mockk(relaxed = true)
    private val handler = ReminderFireHandler(repository, notifier)

    private fun reminder(
        id: String = "t1",
        isCompleted: Boolean = false,
        remindAtIso: String? = "2026-08-29T15:00:00",
        remindedAtMs: Long? = null
    ) = TaskItem(
        id = id, title = "약 먹기", isCompleted = isCompleted, createdAt = 0L,
        remindAtIso = remindAtIso, remindedAtMs = remindedAtMs
    )

    @Test
    fun `미완료·미발화 리마인더는 알림 게시 후 발화 시각을 기록한다`() = runTest {
        coEvery { repository.getById("t1") } returns AppResult.Success(reminder())
        coEvery { repository.markReminded("t1", 999L) } returns AppResult.Success(Unit)

        handler.fire("t1", now = 999L)

        verify(exactly = 1) { notifier.notifyReminder("t1", "약 먹기", "8월 29일 오후 3:00") }
        coVerify(exactly = 1) { repository.markReminded("t1", 999L) }
    }

    @Test
    fun `이미 완료된 할 일은 발화하지 않는다`() = runTest {
        coEvery { repository.getById("t1") } returns AppResult.Success(reminder(isCompleted = true))

        handler.fire("t1", now = 999L)

        verify(exactly = 0) { notifier.notifyReminder(any(), any(), any()) }
        coVerify(exactly = 0) { repository.markReminded(any(), any()) }
    }

    @Test
    fun `이미 발화된 리마인더는 다시 울리지 않는다`() = runTest {
        coEvery { repository.getById("t1") } returns AppResult.Success(reminder(remindedAtMs = 100L))

        handler.fire("t1", now = 999L)

        verify(exactly = 0) { notifier.notifyReminder(any(), any(), any()) }
    }

    @Test
    fun `리마인더가 아닌 할 일은 발화하지 않는다`() = runTest {
        coEvery { repository.getById("t1") } returns AppResult.Success(reminder(remindAtIso = null))

        handler.fire("t1", now = 999L)

        verify(exactly = 0) { notifier.notifyReminder(any(), any(), any()) }
    }

    @Test
    fun `조회 실패·부재 시 조용히 종료한다`() = runTest {
        coEvery { repository.getById("gone") } returns AppResult.Success(null)
        coEvery { repository.getById("err") } returns AppResult.Failure(AppError.DbReadError("task_item"))

        handler.fire("gone", now = 999L)
        handler.fire("err", now = 999L)

        verify(exactly = 0) { notifier.notifyReminder(any(), any(), any()) }
    }
}
