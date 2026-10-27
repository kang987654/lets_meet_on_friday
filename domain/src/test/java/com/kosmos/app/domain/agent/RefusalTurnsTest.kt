package com.kosmos.app.domain.agent

import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 거절 턴 판정·기록에서 빼기(0.38.9 M2). */
class RefusalTurnsTest {

    private fun msg(role: ChatMessage.Role, content: String, at: Long) = ChatMessage("$at", "s1", role, content, InputType.TEXT, createdAt = at)

    @Test
    fun `할 수 없다는 답만 거절로 본다`() {
        listOf("죄송해요, 저는 운동 루틴을 짜드리는 기능은 없어요.", "점심 메뉴 추천은 제가 직접 해드릴 수 없어요.", "저장·관리하는 기능이 없어요.")
            .forEach { assertTrue(it, RefusalTurns.isRefusal(it)) }
        listOf("메모에는 남기지 않았어요.", "현재 등록된 알림은 없어요.", "김치찌개는 김치를 볶은 뒤 물을 부어 끓이면 돼요.")
            .forEach { assertFalse(it, RefusalTurns.isRefusal(it)) }
    }

    @Test
    fun `거절 답과 바로 앞 사용자 말만 빠진다`() {
        val messages = listOf(
            msg(ChatMessage.Role.USER, "알림 뭐 있어?", 1),
            msg(ChatMessage.Role.ASSISTANT, "현재 등록된 알림은 없어요.", 2),
            msg(ChatMessage.Role.USER, "아침 운동 루틴 짜줘", 3),
            msg(ChatMessage.Role.ASSISTANT, "죄송해요, 저는 운동 루틴을 짜드리는 기능은 없어요.", 4),
            msg(ChatMessage.Role.USER, "오늘 날씨 좋다", 5)
        )

        assertEquals(listOf("1", "2", "5"), RefusalTurns.dropFrom(messages).map { it.id })
        assertEquals(messages.take(2), RefusalTurns.dropFrom(messages.take(2)))
    }
}
