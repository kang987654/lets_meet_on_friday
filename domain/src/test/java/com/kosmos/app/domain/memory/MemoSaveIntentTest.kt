package com.kosmos.app.domain.memory

import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 메모 저장 의도 판정(0.38.9 M1) — 사실만 말한 턴의 저장을 막고, 저장 물음에 대한 "응"을 알아본다. */
class MemoSaveIntentTest {

    @Test
    fun `저장 부탁은 목록 밖 표현까지 낱말로 잡고 사실만 말한 문장은 잡지 않는다`() {
        listOf(
            "내 자전거 비밀번호 4821이야 기억해줘", "와이파이 비번 저장해줘", "사번 메모해줘", "땅콩 알레르기 있어, 잊지 마", "생일 적어 둬",
            "이거 알아둬, 사물함 214번이야", "엄마 생신 11월 7일이야, 까먹으면 안 돼", "락커 비번 0912야 기록해줘", "강아지 이름 콩이야 외워 둬", "혈액형 A형인 거 꼭 챙겨줘"
        ).forEach { assertTrue(it, MemoSaveIntent.hasSaveWord(it)) }
        listOf("택배 송장번호 6043 1122 9087이야", "내 사물함 번호는 214번이야", "주차는 지하 2층 C-14에 했어", "우리 강아지 이름은 콩이야", "회의는 오후 4시 30분에 시작해")
            .forEach { assertFalse(it, MemoSaveIntent.hasSaveWord(it)) }
    }

    @Test
    fun `사실만 말했고 앞에서 묻지도 않았으면 막고, 부탁했거나 물어 둔 뒤면 막지 않는다`() {
        assertTrue(MemoSaveIntent.shouldBlock("내 사물함 번호는 214번이야", null))
        assertTrue(MemoSaveIntent.shouldBlock("내 사물함 번호는 214번이야", "메모에는 남기지 않았어요."))
        assertFalse(MemoSaveIntent.shouldBlock("내 사물함 번호는 214번이야 기억해줘", null))
        listOf("사물함 번호 214번 확인했어요. 메모해 둘까요?", "저장해 드릴까요?", "이 내용을 기억해 둘까요?")
            .forEach { assertFalse(it, MemoSaveIntent.shouldBlock("응", it)) }
    }

    @Test
    fun `저장 물음 뒤라도 긍정이 아닌 말이면 막는다(0·39·2 M4b)`() {
        val asked = "고생 많으셨어요. 이 내용을 메모해 둘까요?"
        listOf("그냥 들어줘서 고마워", "아니 됐어", "그래, 근데 내일 일정도 알려줘")
            .forEach { assertTrue(it, MemoSaveIntent.shouldBlock(it, asked)) }
        listOf("응", "그래 부탁해", "좋아요!", "응 저장해줘")
            .forEach { assertFalse(it, MemoSaveIntent.shouldBlock(it, asked)) }
    }

    @Test
    fun `짧은 긍정만 응으로 본다`() {
        listOf("응", "응!", "그래", "좋아요", "부탁해", "네", "ㅇㅇ", "그래 부탁해", "해 줘").forEach { assertTrue(it, MemoSaveIntent.isYes(it)) }
        listOf("아니", "응 근데 오늘 날씨는?", "좋아하는 음식 알려줘", "괜찮아").forEach { assertFalse(it, MemoSaveIntent.isYes(it)) }
    }

    @Test
    fun `저장하지 않은 약속 문장은 빼고 물음이 없으면 붙인다`() {
        assertEquals("메모해 둘까요?", MemoSaveIntent.withoutClaim("콩이라는 강아지 이름을 기억해 둘게요! 😊"))
        assertEquals(
            "네, 다음 주부터 판교로 출근하시는군요. 메모해 둘까요?",
            MemoSaveIntent.withoutClaim("네, 다음 주부터 판교로 출근하시는군요. 그 사실을 기억해 둘게요. 메모해 둘까요?")
        )
        assertEquals("사물함 번호가 214번이시군요. 메모해 둘까요?", MemoSaveIntent.withoutClaim("사물함 번호가 214번이시군요. 메모해 두었어요."))
    }

    @Test
    fun `약속이 아니거나 답 안에 든 낱말이면 그대로 둔다`() {
        listOf(
            "사물함 번호가 214번이시군요. 메모해 둘까요?",
            "전에 저장했던 자전거 비밀번호는 4821이에요.",
            "메모에는 남기지 않았어요.",
            "오늘 일정은 두 건이에요."
        ).forEach { assertEquals(it, it, MemoSaveIntent.withoutClaim(it)) }
    }

    @Test
    fun `앞 턴은 지금 턴의 사용자 메시지를 건너뛴 비서 말과 그 앞 사용자 말`() {
        fun msg(role: ChatMessage.Role, content: String, at: Long) = ChatMessage("$at", "s1", role, content, InputType.TEXT, createdAt = at)
        val recent = listOf(
            msg(ChatMessage.Role.USER, "내 사물함 번호는 214번이야", 1),
            msg(ChatMessage.Role.ASSISTANT, "사물함 번호 214번이시군요. 메모해 둘까요?", 2),
            msg(ChatMessage.Role.USER, "응", 3)
        )
        assertEquals(
            MemoSaveIntent.PreviousTurn("사물함 번호 214번이시군요. 메모해 둘까요?", "내 사물함 번호는 214번이야"),
            MemoSaveIntent.previousTurn(recent.shuffled())
        )
        assertEquals(MemoSaveIntent.PreviousTurn(null, null), MemoSaveIntent.previousTurn(listOf(msg(ChatMessage.Role.USER, "안녕", 1))))
    }
}
