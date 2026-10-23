package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.TaskItem
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [ListRemindersToolExecutorTest]
 * 알림 목록 툴(0.38.5 M5) — 울리기 전 알림을 시각순 "[알림]" 묶음으로, 시각은 한국어 표기로 돌려준다.
 */
@RunWith(RobolectricTestRunner::class)
class ListRemindersToolExecutorTest {

    private fun task(id: String, title: String, at: String) =
        TaskItem(id = id, title = title, isCompleted = false, createdAt = 0L, remindAtIso = at)

    private fun run(result: AppResult<List<TaskItem>>): JSONObject {
        val repo: TaskRepository = mockk { coEvery { getActiveReminders() } returns result }
        return JSONObject(runBlocking { ListRemindersToolExecutor(repo).execute(ToolArguments(JSONObject()), "s1") })
    }

    @Test
    fun `울리기 전 알림을 시각순으로, 시각은 한국어로`() {
        val json = run(AppResult.Success(listOf(task("b", "약 먹기", "2026-10-24T07:00:00"), task("a", "세탁물 찾기", "2026-10-23T18:30:00"))))
        assertEquals("success", json.getString("status"))
        assertEquals("[알림]\n- 세탁물 찾기 (10월 23일 오후 6:30)\n- 약 먹기 (10월 24일 오전 7:00)", json.getString("data"))
    }

    @Test
    fun `알림이 없으면 없다고 말한다`() {
        assertEquals("등록된 알림이 없어요.", run(AppResult.Success(emptyList())).getString("data"))
    }
}
