package com.kosmos.app.domain.memory

import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import org.junit.Assert.assertEquals
import org.junit.Test

/** 기억 검색 결과 맨 위 "방금 이 대화" 줄 고르기(0.38.8 M2). */
class CurrentConversationLinesTest {

    private val now = 10_000_000L

    private fun msg(role: ChatMessage.Role, content: String, agoMs: Long) =
        ChatMessage(id = content, sessionId = "s", role = role, content = content, inputType = InputType.TEXT, createdAt = now - agoMs)

    @Test
    fun `낱말이 겹치는 최근 사용자 말을 최신 먼저 — 지금 던진 질문과 비서 말·창 밖은 뺀다`() {
        val messages = listOf(
            msg(ChatMessage.Role.USER, "회의실 어디라고 했지?", 1_000),               // 지금 질문
            msg(ChatMessage.Role.ASSISTANT, "회의실 정보는 메모에 없어요.", 60_000),
            msg(ChatMessage.Role.USER, "다음 주 금요일 회의실은 5층 C룸이야", 120_000),
            msg(ChatMessage.Role.USER, "점심 뭐 먹지", 180_000),
            msg(ChatMessage.Role.USER, "회의실은 3층 B룸이야", CurrentConversationLines.WINDOW_MS + 1)  // 창 밖
        )

        assertEquals(listOf("다음 주 금요일 회의실은 5층 C룸이야"), CurrentConversationLines.pick(messages, listOf("회의실"), now))
    }

    @Test
    fun `겹치는 말이 없으면 비어 있다`() {
        val messages = listOf(msg(ChatMessage.Role.USER, "와이파이 비번 뭐였지?", 1_000), msg(ChatMessage.Role.USER, "오늘 날씨 좋다", 60_000))
        assertEquals(emptyList<String>(), CurrentConversationLines.pick(messages, listOf("와이파이"), now))
    }
}
