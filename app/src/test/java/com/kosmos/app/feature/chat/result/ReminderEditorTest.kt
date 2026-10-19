package com.kosmos.app.feature.chat.result

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.MessageResult
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.platform.alarm.ReminderAlarmScheduler
import com.kosmos.app.widget.WidgetRefresher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * 알림 수정 시트(0.38.2 M4)의 동작 — 할 일·알람·대화 한 줄(D8)·다음 턴 대화 재구성이 함께 바뀌는지.
 * 시각·시간대는 고정한다(AGENTS §2-④).
 */
class ReminderEditorTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = requireNotNull(IsoDateTimeParser.toEpochMillis("2026-10-15T13:30:00", zone))
    private val task = TaskItem(id = "t1", title = "물 마시기", isCompleted = false, createdAt = 0, remindAtIso = "2026-10-15T13:56:00")

    private val tasks: TaskRepository = mockk(relaxed = true)
    private val alarms: ReminderAlarmScheduler = mockk(relaxed = true)
    private val widget: WidgetRefresher = mockk(relaxed = true)
    private val audit: AuditTrailService = mockk(relaxed = true)
    private val conversations: ConversationRepository = mockk(relaxed = true)
    private val sessions: SessionStore = mockk { coEvery { activeSessionIdFlow } returns flowOf("s-now") }
    private val runner: ModelRunner = mockk(relaxed = true)
    private val editor = ReminderEditor(tasks, alarms, widget, audit, conversations, sessions, runner)

    private val savedTask = slot<TaskItem>()
    private val savedLine = slot<ChatMessage>()

    private fun stub() {
        coEvery { tasks.getById("t1") } returns AppResult.Success(task)
        coEvery { tasks.save(capture(savedTask)) } returns AppResult.Success(Unit)
        coEvery { conversations.save(capture(savedLine)) } returns AppResult.Success(Unit)
    }

    @Test
    fun `10분 미루기 — 원래 시각에서 10분 뒤로, 다시 울릴 대상, 알람 재예약, 지금 세션에 한 줄, 다음 턴 재구성`() = runTest {
        stub()

        val result = editor.snooze("t1", now = now, zone = zone)

        assertTrue(result is AppResult.Success)
        assertEquals("2026-10-15T14:06:00", savedTask.captured.remindAtIso)
        assertNull(savedTask.captured.remindedAtMs)
        verify { alarms.schedule("t1", requireNotNull(IsoDateTimeParser.toEpochMillis("2026-10-15T14:06:00", zone))) }
        coVerify { widget.refresh() }
        assertEquals("'물 마시기' 알림을 오후 2:06으로 미뤘어요.", savedLine.captured.content)
        assertEquals("s-now", savedLine.captured.sessionId)
        assertEquals(MessageResult(MessageResult.Kind.REMINDER, "t1"), savedLine.captured.result)
        verify { runner.invalidateConversation() }
    }

    @Test
    fun `이미 울린 알림을 미루면 지금에서 10분 뒤`() = runTest {
        stub()
        coEvery { tasks.getById("t1") } returns AppResult.Success(task.copy(remindAtIso = "2026-10-15T13:00:00", remindedAtMs = 1))

        editor.snooze("t1", now = now, zone = zone)

        assertEquals("2026-10-15T13:40:00", savedTask.captured.remindAtIso)
    }

    @Test
    fun `시간 바꾸기 — 받침 없는 숫자는 '로', 지난 시각은 거부하고 아무것도 바꾸지 않는다`() = runTest {
        stub()

        editor.reschedule("t1", 15, 5, now = now, zone = zone)
        assertEquals("'물 마시기' 알림을 오후 3:05로 바꿨어요.", savedLine.captured.content)

        val past = editor.reschedule("t1", 9, 0, now = now, zone = zone)
        assertTrue(((past as AppResult.Failure).error as AppError.ValidationError).reason.contains("지난"))
        coVerify(exactly = 1) { tasks.save(any()) }
    }

    @Test
    fun `알림 취소 — 행은 남기고 취소 시각, 알람 끔, 한 줄`() = runTest {
        stub()

        editor.cancel("t1", now = now)

        assertEquals(now, savedTask.captured.cancelledAt)
        verify { alarms.cancel("t1") }
        assertEquals("'물 마시기' 알림을 취소했어요.", savedLine.captured.content)
        coVerify(exactly = 0) { tasks.delete(any()) }
    }
}
