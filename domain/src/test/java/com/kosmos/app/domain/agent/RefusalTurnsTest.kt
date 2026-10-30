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

    @Test
    fun `거절을 되묻는 말`() {
        listOf("왜 안 된다고 했어?", "아까 왜 못 한다고 했어?", "안 된다고 했잖아, 이유가 뭐야?", "그게 왜 안 돼?")
            .forEach { assertTrue(it, RefusalTurns.asksWhyRefused(it)) }
        listOf("오늘 날씨 좋다", "점심 메뉴 추천해 줘", "안녕").forEach { assertFalse(it, RefusalTurns.asksWhyRefused(it)) }
    }

    @Test
    fun `할 수 없다면서 도운 답은 거절이 아니다(0·39·2 M2)`() {
        listOf(
            "정확한 진단은 해 드릴 수 없지만, 물을 충분히 마시는 게 도움이 돼요.",
            "전화를 직접 걸어 드릴 수는 없지만, 예약 번호 A-302로 문의하시면 돼요.",
            "예약 기능은 없는데, 일정으로 추가해 드릴까요?"
        ).forEach { assertFalse(it, RefusalTurns.isRefusal(it)) }
        assertTrue(RefusalTurns.isRefusal("실시간 날씨는 확인해 드릴 수 없어요. 대신 일정은 도와드릴게요."))
    }

    @Test
    fun `일상의 왜 안은 앞 대화를 가리킬 때만 되묻기다(0·39·2 M2)`() {
        listOf("와이파이가 왜 안 돼?", "다이어트가 왜 안 될까?", "알림이 왜 안 울렸지?", "요즘 왜 못 자는 걸까?")
            .forEach { assertFalse(it, RefusalTurns.asksWhyRefused(it)) }
        listOf("아까 그거 왜 안 돼?", "방금 왜 못 한다고 했어?", "그건 왜 안 되는데?")
            .forEach { assertTrue(it, RefusalTurns.asksWhyRefused(it)) }
    }

    @Test
    fun `가장 최근 사용자 말이 거절을 되물으면 거절 턴을 남긴다`() {
        val refused = listOf(
            msg(ChatMessage.Role.USER, "아침 운동 루틴 짜줘", 1),
            msg(ChatMessage.Role.ASSISTANT, "죄송해요, 저는 운동 루틴을 짜드리는 기능은 없어요.", 2)
        )

        assertEquals(listOf("1", "2", "3"), RefusalTurns.forModel(refused + msg(ChatMessage.Role.USER, "왜 안 된다고 했어?", 3)).map { it.id })
        assertEquals(listOf("3"), RefusalTurns.forModel(refused + msg(ChatMessage.Role.USER, "점심 메뉴 추천해 줘", 3)).map { it.id })
    }
}
