package com.kosmos.app.feature.chat.result

import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.MessageResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 상황 칩 규칙(0.38.2 M6) — 코드 규칙만, 추론 없음. 규칙에 안 걸리면 칩 없음.
 */
@RunWith(RobolectricTestRunner::class) // org.json 은 JVM 단위 테스트에서 스텁이다
class ChatChipRulesTest {

    private fun labels(text: String) = ChatChipRules.answerChips(text).map { it.label }

    @Test
    fun `양자택일 질문이면 다른 부분만 두 칩 — 누르면 그 글자로 보낸다`() {
        assertEquals(listOf("진한 쪽", "부드러운 쪽"), labels("오후니까 디카페인 라떼 어때요? 진한 쪽이 좋으세요, 부드러운 쪽이 좋으세요?"))
        assertEquals(listOf("오전", "오후"), labels("오전이 좋으세요 아니면 오후가 좋으세요?"))
        assertEquals(listOf("커피로", "차로"), labels("커피로 할까요, 차로 할까요?"))
        assertEquals(ChatChip.Action.Send("진한 쪽"), ChatChipRules.answerChips("진한 쪽이 좋으세요, 부드러운 쪽이 좋으세요?").first().action)
    }

    @Test
    fun `양자택일이 아니면 칩 없음`() {
        assertEquals(emptyList<String>(), labels("알림을 오후 2시로 맞췄어요."))
        assertEquals(emptyList<String>(), labels("어떤 걸로 할까요?"))
        assertEquals(emptyList<String>(), labels("커피로 할까요, 차는 어때요?"))
        assertEquals(emptyList<String>(), labels("진한 쪽이 좋으세요, 부드러운 쪽이 좋으세요? 알려 주시면 골라 드릴게요."))
        assertEquals(emptyList<String>(), labels("A, B, C 중에 뭐가 좋으세요?"))
    }

    @Test
    fun `행동 칩 — 충돌이 있으면 과거 기록 정리, 기억·메모는 메모 보기, 일정은 캘린더, 알림은 없음`() {
        val meta = """{"conflictCount":2,"conflictEpisodeIds":["e1","e2"]}"""
        val recall = ChatChipRules.actionChips(MessageResult(MessageResult.Kind.MEMORY_RECALL, "k1", meta))
        assertEquals(listOf("과거 기록 2건 정리", "메모 보기"), recall.map { it.label })
        assertEquals(ChatChip.Action.OpenEpisode("e1"), recall.first().action)
        assertEquals(listOf("메모 보기"), ChatChipRules.actionChips(MessageResult(MessageResult.Kind.MEMORY_RECALL, "k1", """{"noteAt":1}""")).map { it.label })
        assertEquals(listOf("캘린더에서 보기"), ChatChipRules.actionChips(MessageResult(MessageResult.Kind.SCHEDULE, "t1")).map { it.label })
        assertEquals(emptyList<ChatChip>(), ChatChipRules.actionChips(MessageResult(MessageResult.Kind.REMINDER, "t1")))
    }

    @Test
    fun `사용자 메시지에는 칩 없음`() {
        val user = ChatMessage(id = "m", sessionId = "s", role = ChatMessage.Role.USER, content = "진한 쪽이 좋으세요, 부드러운 쪽이 좋으세요?", inputType = InputType.TEXT, createdAt = 0)
        assertEquals(emptyList<ChatChip>(), ChatChipRules.of(user))
    }
}
