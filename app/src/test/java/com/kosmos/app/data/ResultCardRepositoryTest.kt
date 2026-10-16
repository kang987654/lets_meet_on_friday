package com.kosmos.app.data

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.repository.ConversationRepositoryImpl
import com.kosmos.app.data.local.repository.TaskRepositoryImpl
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.MessageResult
import com.kosmos.app.domain.model.TaskItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [ResultCardRepositoryTest]
 * 스키마 10판(0.38.2)의 저장소 계약 — 메시지 결과가 저장·재로드에서 살아남고, 취소한 할 일이 목록·개수·알람 대상에서 빠진다(결정 1).
 */
@RunWith(RobolectricTestRunner::class)
class ResultCardRepositoryTest {

    @get:org.junit.Rule
    val dbRule = com.kosmos.app.testing.InMemoryKosmosDbRule()

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).data

    private fun message(id: String, result: MessageResult?) = ChatMessage(
        id = id, sessionId = "s1", role = ChatMessage.Role.ASSISTANT, content = "답", inputType = InputType.TEXT,
        createdAt = 1, result = result
    )

    @Test
    fun `메시지 결과가 저장·재로드에서 그대로 — 결과 없는 메시지는 null`() = runBlocking {
        val repo = ConversationRepositoryImpl(dbRule.db.conversationDao())
        val withResult = MessageResult(MessageResult.Kind.MEMORY_RECALL, "k1", """{"conflicts":1}""")
        repo.save(message("m1", withResult)).value()
        repo.save(message("m2", null)).value()

        val loaded = repo.getRecentBySession("s1", 10).value().associateBy { it.id }

        assertEquals(withResult, loaded.getValue("m1").result)
        assertNull(loaded.getValue("m2").result)
    }

    @Test
    fun `취소한 할 일은 남은 할 일·완료됨·개수·알람 대상에서 빠지고 행은 남는다`() = runBlocking {
        val repo = TaskRepositoryImpl(dbRule.db.taskDao())
        repo.save(TaskItem(id = "live", title = "물 마시기", isCompleted = false, createdAt = 2, remindAtIso = "2026-10-15T13:56:00")).value()
        repo.save(TaskItem(id = "gone", title = "스트레칭", isCompleted = false, createdAt = 1, remindAtIso = "2026-10-15T15:40:00", cancelledAt = 5)).value()
        repo.save(TaskItem(id = "doneGone", title = "장보기", isCompleted = true, createdAt = 0, cancelledAt = 5)).value()

        assertEquals(listOf("live"), repo.getPendingTasksData(0, 10).value().map { it.id })
        assertEquals(emptyList<String>(), repo.getCompletedTasksData(0, 10).value().map { it.id })
        assertEquals(1, repo.getCounts().value().pending)
        assertEquals(0, repo.getCounts().value().completed)
        assertEquals(listOf("live"), repo.getActiveReminders().value().map { it.id })
        assertEquals(5L, repo.getById("gone").value()?.cancelledAt)
    }
}
