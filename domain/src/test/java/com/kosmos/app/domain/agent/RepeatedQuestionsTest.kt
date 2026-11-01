package com.kosmos.app.domain.agent

import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 같은 질문의 앞 답 빼기(0.39.3 M4d). */
class RepeatedQuestionsTest {

    private fun msg(role: ChatMessage.Role, content: String, at: Long) = ChatMessage("$at", "s1", role, content, InputType.TEXT, createdAt = at)

    @Test
    fun `같은 질문은 표현이 조금 달라도 같다고 보고 진술이나 다른 질문은 아니다`() {
        assertTrue(RepeatedQuestions.same("내가 알려준 자전거 비밀번호 뭐였지?", "내가 알려준 자전거 비밀번호 뭐였지?"))
        assertTrue(RepeatedQuestions.same("자전거 비밀번호 뭐였지", "내가 알려준 자전거 비밀번호 뭐였지?"))
        assertFalse("진술은 질문이 아니다", RepeatedQuestions.same("내 자전거 비밀번호 4821이야 기억해줘", "자전거 비밀번호 뭐였지?"))
        assertFalse(RepeatedQuestions.same("회의실 어디라고 했지?", "자전거 비밀번호 뭐였지?"))
    }

    @Test
    fun `지금 질문과 같은 앞 질문과 그 답만 빠진다`() {
        val messages = listOf(
            msg(ChatMessage.Role.USER, "내가 알려준 자전거 비밀번호 뭐였지?", 1),
            msg(ChatMessage.Role.ASSISTANT, "자전거 비밀번호는 134예요.", 2),
            msg(ChatMessage.Role.USER, "내 자전거 비밀번호 4821이야 기억해줘", 3),
            msg(ChatMessage.Role.ASSISTANT, "메모에는 남기지 않았어요.", 4),
            msg(ChatMessage.Role.USER, "내가 알려준 자전거 비밀번호 뭐였지?", 5)
        )

        assertEquals(listOf("3", "4", "5"), RepeatedQuestions.forModel(messages).map { it.id })
        val other = messages.dropLast(1) + msg(ChatMessage.Role.USER, "점심 메뉴 추천해 줘", 5)
        assertEquals(other, RepeatedQuestions.forModel(other))
    }
}
