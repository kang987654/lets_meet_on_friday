package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.usecase.AddReminderUseCase
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.platform.alarm.ReminderAlarmScheduler
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AddReminderToolExecutorTest]
 * 리마인더 툴 인자 검증이 **승인 시트가 뜨기 전에** 동작하고, 실행이 저장→알람 예약으로
 * 이어지는지 고정합니다 (AddScheduleToolExecutorTest 패턴).
 */
@RunWith(RobolectricTestRunner::class)
class AddReminderToolExecutorTest {

    private val useCase: AddReminderUseCase = mockk()
    private val scheduler: ReminderAlarmScheduler = mockk(relaxed = true)
    private val widgetRefresher: com.kosmos.app.widget.WidgetRefresher = mockk(relaxed = true)
    // [WHY] 고정 시각 — 픽스처 날짜(2026-08-29)가 벽시계 기준 과거가 되어도 승인 요청이 만들어지게.
    private val fixedNow = requireNotNull(IsoDateTimeParser.toEpochMillis("2026-08-29T09:00:00"))
    private val executor = AddReminderToolExecutor(useCase, scheduler, widgetRefresher) { fixedNow }

    private fun args(json: String) = ToolArguments(JSONObject(json))

    @Test
    fun `깨진 time 으로는 승인 요청이 만들어지지 않는다`() {
        val e = runCatching {
            executor.buildApprovalRequest(
                args("""{"time":"내일 3시쯤","content":"약 먹기"}"""),
                sessionId = "s1"
            )
        }.exceptionOrNull() as ToolArgumentException
        assertEquals("time", e.field)
        assertEquals(ToolArgumentException.Reason.BAD_FORMAT, e.reason)
    }

    @Test
    fun `content 가 없으면 승인 요청이 만들어지지 않는다`() {
        val e = runCatching {
            executor.buildApprovalRequest(
                args("""{"time":"2026-08-29T15:00:00"}"""),
                sessionId = "s1"
            )
        }.exceptionOrNull() as ToolArgumentException
        assertEquals("content", e.field)
        assertEquals(ToolArgumentException.Reason.MISSING, e.reason)
    }

    @Test
    fun `승인 요청은 한국어 표기를 쓰고 ISO 원문과 전용 카드가 없다`() {
        val request = executor.buildApprovalRequest(
            args("""{"time":"2026-08-29T15:00:00","content":"약 먹기"}"""),
            sessionId = "s1"
        )

        assertEquals("리마인더 등록", request.title)
        assertTrue(request.description.contains("8월 29일 오후 3:00"))
        assertTrue(request.description.contains("약 먹기"))
        assertFalse("ISO 원문이 사용자에게 노출되면 안 된다", request.description.contains("2026-08-29T"))
        assertNull("전용 카드 없음 — 기본 ApprovalSheet 경로", request.calendarDraft)
    }

    @Test
    fun `지난 시각으로는 승인 요청이 만들어지지 않는다`() {
        val e = runCatching {
            executor.buildApprovalRequest(
                args("""{"time":"2026-08-29T08:59:00","content":"약 먹기"}"""),
                sessionId = "s1"
            )
        }.exceptionOrNull() as ToolArgumentException
        assertEquals("time", e.field)
        assertEquals(ToolArgumentException.Reason.PAST, e.reason)
    }

    @Test
    fun `지금과 같은 시각도 지난 시각이다`() {
        val e = runCatching {
            executor.buildApprovalRequest(
                args("""{"time":"2026-08-29T09:00:00","content":"약 먹기"}"""),
                sessionId = "s1"
            )
        }.exceptionOrNull() as ToolArgumentException
        assertEquals(ToolArgumentException.Reason.PAST, e.reason)
    }

    @Test
    fun `실행은 저장 후 저장된 항목의 알람을 예약한다`() = runTest {
        val saved = TaskItem(
            id = "task-1", title = "약 먹기", isCompleted = false, createdAt = 0L,
            remindAtIso = "2026-08-29T15:00:00"
        )
        // [WHY] now 는 기본 인자(벽시계) — 기본값 브리지가 실제 시각을 계산해 넘기므로 any().
        coEvery { useCase("약 먹기", "2026-08-29T15:00:00", any()) } returns AppResult.Success(saved)

        val result = executor.execute(
            args("""{"time":"2026-08-29T15:00:00","content":"약 먹기"}"""),
            sessionId = "s1"
        )

        val expectedMs = requireNotNull(IsoDateTimeParser.toEpochMillis("2026-08-29T15:00:00"))
        verify(exactly = 1) { scheduler.schedule("task-1", expectedMs) }
        io.mockk.coVerify(exactly = 1) { widgetRefresher.refresh() }
        val json = JSONObject(result)
        assertEquals("success", json.getString("status"))
        assertTrue(json.getString("message").contains("8월 29일 오후 3:00"))
    }

    @Test
    fun `과거 시각이면 모델이 자가수정할 수 있는 오류 관측값을 돌려준다`() = runTest {
        coEvery { useCase(any(), any(), any()) } returns
            AppResult.Failure(AppError.ValidationError("time", "이미 지난 시각입니다"))

        val result = executor.execute(
            args("""{"time":"2020-01-01T09:00:00","content":"약 먹기"}"""),
            sessionId = "s1"
        )

        val json = JSONObject(result)
        assertEquals("error", json.getString("status"))
        assertTrue(json.getString("message").contains("이미 지난 시각"))
        verify(exactly = 0) { scheduler.schedule(any(), any()) }
        io.mockk.coVerify(exactly = 0) { widgetRefresher.refresh() }
    }
}
