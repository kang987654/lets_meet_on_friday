package com.kosmos.app.assistant.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DocumentTurnReminderTest]
 * 문서가 첨부된 턴은 턴 리마인더만 문서용으로 바뀐다 (0.35.0, exp46b) — 시스템 지시·히스토리는 그대로라 대화 KV 재사용이 깨지지 않는다.
 * 실험실이 앱과 같은 문구를 쓰도록 `doc_turn_reminder.txt` 픽스처로도 내보낸다(PromptFixtureExportTest 와 같은 규칙).
 */
class DocumentTurnReminderTest {

    private val context = ContextBuilder.Context(
        recentConversations = emptyList(),
        sessionId = "doc-turn",
        responseStyle = "DEFAULT",
        webSearchEnabled = false
    )
    private val tools = listOf("add_schedule", "get_schedule", "add_memory", "search_memory", "add_reminder")
    private val question = "10월 3일에 산 책은 얼마였어?"

    private fun assemble(documentAttached: Boolean) =
        PromptAssembler().assembleWithTools(context, question, tools, "personal assistant named Kosmos", documentAttached = documentAttached)

    @Test
    fun `문서 턴은 리마인더만 바뀌고 시스템 지시는 같다`() {
        val normal = assemble(documentAttached = false)
        val document = assemble(documentAttached = true)

        assertEquals("시스템 지시가 바뀌면 문서 턴마다 대화를 다시 프리필한다", normal.systemInstruction, document.systemInstruction)
        assertTrue(document.currentInput.startsWith("[Attached Document] The user attached a document above."))
        assertTrue(document.currentInput.contains("Call a tool only if the user explicitly asks to save, schedule, or remind"))
        assertFalse("표준 리마인더(MUST call)는 빠진다", document.currentInput.contains("you MUST call the tool"))
        assertTrue(document.currentInput.endsWith(question))
        assertTrue("보통 턴은 그대로", normal.currentInput.contains("For THIS request"))

        val dir = File("../scratch/lab/fixtures")
        if (dir.isDirectory) File(dir, "doc_turn_reminder.txt").writeText(document.currentInput.removeSuffix(question).trim())
    }

    @Test
    fun `툴이 없는 턴에는 리마인더를 붙이지 않는다`() {
        val prompt = PromptAssembler().assembleWithTools(context, question, emptyList(), "assistant", documentAttached = true)
        assertEquals(question, prompt.currentInput)
    }
}
