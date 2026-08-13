package com.kosmos.app.assistant.tool

import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AddScheduleToolExecutorTest]
 * 일정 툴 인자 검증이 **승인 카드가 뜨기 전에** 동작하는지 고정합니다.
 *
 * [WHY] 실기기에서 모델이 `202026-081717T010000` 같은 깨진 타임스탬프를 생성해 승인 카드에
 * 그대로 노출됐다(2026-08-13). buildApprovalRequest 가 검증을 통과해야만 카드가 뜨는 구조이므로,
 * 여기서 던지는 BAD_FORMAT 이 곧 "사용자가 쓰레기 초안을 보지 않는다"는 계약이다.
 */
@RunWith(RobolectricTestRunner::class)
class AddScheduleToolExecutorTest {

    private val executor = AddScheduleToolExecutor(mockk())

    private fun args(json: String) = ToolArguments(JSONObject(json))

    @Test
    fun `깨진 startTime 으로는 승인 요청이 만들어지지 않는다`() {
        val e = runCatching {
            executor.buildApprovalRequest(
                args("""{"title":"팀 회의","startTime":"202026-081717T010000"}"""),
                sessionId = "s1"
            )
        }.exceptionOrNull() as ToolArgumentException
        assertEquals("startTime", e.field)
        assertEquals(ToolArgumentException.Reason.BAD_FORMAT, e.reason)
    }

    @Test
    fun `유효한 인자로는 초안이 원문 그대로 만들어진다`() {
        val request = executor.buildApprovalRequest(
            args("""{"title":"팀 회의","startTime":"2026-08-17T10:00:00"}"""),
            sessionId = "s1"
        )
        val draft = request.calendarDraft
        assertNotNull("캘린더 초안 카드용 구조화 초안이 있어야 한다", draft)
        assertEquals("팀 회의", draft?.title)
        // 검증은 통과/거부만 하고 값은 바꾸지 않는다 — 표시·저장 경로의 문자열 계약 유지.
        assertEquals("2026-08-17T10:00:00", draft?.startIso)
    }

    @Test
    fun `endTime 은 생략해도 된다`() {
        val request = executor.buildApprovalRequest(
            args("""{"title":"팀 회의","startTime":"2026-08-17T10:00:00"}"""),
            sessionId = "s1"
        )
        assertEquals(null, request.calendarDraft?.endIso)
    }

    @Test
    fun `endTime 이 있는데 깨졌으면 형식 오류다`() {
        val e = runCatching {
            executor.buildApprovalRequest(
                args("""{"title":"팀 회의","startTime":"2026-08-17T10:00:00","endTime":"111월"}"""),
                sessionId = "s1"
            )
        }.exceptionOrNull() as ToolArgumentException
        assertEquals("endTime", e.field)
        assertEquals(ToolArgumentException.Reason.BAD_FORMAT, e.reason)
    }
}
