package com.kosmos.app.domain.agent

import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [IdleGapHistoryTest]
 * 오래 쉰 간격 앞을 지난 대화로 가르는 판정(0.41.7 M4).
 */
class IdleGapHistoryTest {

    private val gap = 30 * 60_000L
    private fun m(id: String, at: Long) = ChatMessage(id, "s1", ChatMessage.Role.USER, id, InputType.TEXT, createdAt = at)

    @Test
    fun `마지막 긴 간격에서 가른다`() {
        val split = IdleGapHistory.split(listOf(m("a", 0), m("b", gap + 1), m("c", gap + 2), m("d", 3 * gap)), now = 3 * gap, gapMs = gap)
        assertEquals(listOf("a", "b", "c"), split.previous.map { it.id })
        assertEquals(listOf("d"), split.current.map { it.id })
    }

    @Test
    fun `경계값 — 정확히 30분은 같은 대화다`() {
        val split = IdleGapHistory.split(listOf(m("a", 0), m("b", gap)), now = gap, gapMs = gap)
        assertTrue(split.previous.isEmpty())
        assertEquals(2, split.current.size)
    }

    @Test
    fun `마지막 메시지 뒤로 이미 오래 지났으면 전부 지난 대화다`() {
        val split = IdleGapHistory.split(listOf(m("a", 0), m("b", 1)), now = gap + 2, gapMs = gap)
        assertEquals(2, split.previous.size)
        assertTrue(split.current.isEmpty())
    }

    @Test
    fun `빈 기록은 빈 둘이다`() {
        val split = IdleGapHistory.split(emptyList(), now = 0, gapMs = gap)
        assertTrue(split.previous.isEmpty() && split.current.isEmpty())
    }

    @Test
    fun `요약 줄은 기억 검색과 같은 꼬리표를 쓴다`() {
        assertEquals("(과거 대화) 제목: 한 줄\n아님".replace("\n", " "), IdleGapHistory.line("제목", "한 줄\n아님"))
        assertEquals("(Past conversation) Title: body", IdleGapHistory.line("Title", "body", AppLanguage.EN))
        assertEquals("(과거 대화) 본문만", IdleGapHistory.line(null, "본문만"))
    }
}
