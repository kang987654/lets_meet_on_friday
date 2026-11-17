package com.kosmos.app.domain.agent

import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.domain.tool.ToolNames
import com.kosmos.app.domain.usecase.buildEpisodeTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [RejectionRepliesTest]
 * 승인 거절 문장(0.42.3 M1) — 문장은 다음 턴 모델 입력이라 바이트가 그대로여야 하고, 요약 전사는 그 자리를 사용자 쪽 "취소함" 줄로 바꾼다.
 */
class RejectionRepliesTest {

    @Test
    fun `거절 문장은 옮기기 전과 같은 바이트다`() {
        assertEquals("일정을 추가하지 않았어요.", RejectionReplies.reply(ToolNames.ADD_SCHEDULE))
        assertEquals("알림을 등록하지 않았어요.", RejectionReplies.reply(ToolNames.ADD_REMINDER))
        assertEquals("메모에는 남기지 않았어요.", RejectionReplies.reply(ToolNames.ADD_MEMORY))
        assertEquals("요청을 실행하지 않았어요.", RejectionReplies.reply("other"))
        assertEquals("I didn't add the event.", RejectionReplies.reply(ToolNames.ADD_SCHEDULE, AppLanguage.EN))
        assertEquals("I didn't set the reminder.", RejectionReplies.reply(ToolNames.ADD_REMINDER, AppLanguage.EN))
        assertEquals("I didn't save that as a note.", RejectionReplies.reply(ToolNames.ADD_MEMORY, AppLanguage.EN))
        assertEquals("I didn't run the request.", RejectionReplies.reply("other", AppLanguage.EN))
    }

    @Test
    fun `요약 전사는 거절 문장을 사용자 쪽 취소 줄로 바꾸고 다른 비서 말은 그대로 둔다`() {
        val messages = listOf(
            msg(ChatMessage.Role.USER, "금요일 저녁 7시 친구들이랑 저녁 약속 잡아줘", 1),
            msg(ChatMessage.Role.ASSISTANT, "일정을 추가하지 않았어요.", 2),
            msg(ChatMessage.Role.USER, "주차 B2-17 기억해줘", 3),
            msg(ChatMessage.Role.ASSISTANT, "메모에는 남기지 않았어요.", 4),
            msg(ChatMessage.Role.ASSISTANT, "일정을 추가하지 않았어요. 다른 날로 잡을까요?", 5)
        )
        val text = buildEpisodeTranscript(messages, charTokenizer, 10_000, AppLanguage.KO)
        assertEquals(
            "사용자: 금요일 저녁 7시 친구들이랑 저녁 약속 잡아줘\n" +
                "사용자: (승인 창에서 일정 추가를 취소함)\n" +
                "사용자: 주차 B2-17 기억해줘\n" +
                "사용자: (승인 창에서 메모 저장을 취소함)\n" +
                "비서: 일정을 추가하지 않았어요. 다른 날로 잡을까요?",
            text
        )
        assertNull("거절 문장이 아니면 바꾸지 않는다", RejectionReplies.cancelLine("그냥 안녕"))
    }

    @Test
    fun `영어 전사도 같은 모양으로 바꾼다`() {
        val text = buildEpisodeTranscript(
            listOf(msg(ChatMessage.Role.USER, "Remind me at 3pm", 1), msg(ChatMessage.Role.ASSISTANT, "I didn't set the reminder.", 2)),
            charTokenizer, 10_000, AppLanguage.EN
        )
        assertEquals("User: Remind me at 3pm\nUser: (Canceled setting the reminder in the approval sheet)", text)
    }

    private val charTokenizer = object : Tokenizer {
        override fun sizeInTokens(text: String): Int = text.length
    }

    private fun msg(role: ChatMessage.Role, content: String, at: Long) = ChatMessage(
        id = "m$at", sessionId = "s", role = role, content = content, inputType = InputType.TEXT, createdAt = at
    )
}
