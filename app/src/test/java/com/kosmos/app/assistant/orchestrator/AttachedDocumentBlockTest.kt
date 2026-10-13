package com.kosmos.app.assistant.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [AttachedDocumentBlockTest]
 * 저장 형식은 모델 입력이라 바이트가 그대로여야 하고(TokenBudgetInvariantTest 의 storedForm), 화면은 그 형식만 알아봐야 한다.
 */
class AttachedDocumentBlockTest {

    @Test
    fun `저장 형식은 기존 바이트 그대로이고 본문을 되돌린다`() {
        val body = "항목: 출시, 담당: 박지훈"
        val stored = AttachedDocumentBlock.wrap(body)

        assertEquals("[Attached Document]\n\"\"\"\n$body\n\"\"\"", stored)
        assertEquals(body, AttachedDocumentBlock.bodyOrNull(stored))
    }

    @Test
    fun `평범한 사용자 메시지는 첨부로 보지 않는다`() {
        assertNull(AttachedDocumentBlock.bodyOrNull("[Attached Document] 라고 직접 적은 메시지"))
        assertNull(AttachedDocumentBlock.bodyOrNull("안녕"))
    }
}
