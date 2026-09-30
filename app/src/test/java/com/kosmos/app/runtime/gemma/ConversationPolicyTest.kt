package com.kosmos.app.runtime.gemma

import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ConversationResetEvent
import com.kosmos.app.domain.modelrunner.ToolResponseInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * [ConversationPolicyTest]
 * 대화 재사용·재생성 판정의 우선순위를 고정합니다 — GemmaModelRunner 안에 있던 동안은 네이티브
 * 엔진 없이 검증할 방법이 없었다.
 */
class ConversationPolicyTest {

    private fun prompt(
        session: String = "s1",
        system: String = "sys",
        tools: List<String> = listOf("AddSchedule"),
        toolResponse: ToolResponseInput? = null,
        budget: Int = 1700
    ) = ChatPrompt(
        sessionId = session,
        systemInstruction = system,
        history = emptyList(),
        currentInput = "hi",
        enabledTools = tools,
        toolResponse = toolResponse,
        contextBudgetTokens = budget
    )

    private val cached = ConversationKey("s1", "sys", listOf("AddSchedule"))

    @Test
    fun `캐시가 없으면 사유 없이 새로 만든다`() {
        assertEquals(ConversationDecision.Recreate(null), decideConversation(null, prompt()) { 0 })
    }

    @Test
    fun `같은 키이고 임계값 이하면 재사용`() {
        assertEquals(ConversationDecision.Reuse, decideConversation(cached, prompt()) { 100 })
    }

    @Test
    fun `툴 회신 턴은 토큰 초과여도 재사용하고 토큰을 묻지도 않는다`() {
        var asked = false
        val decision = decideConversation(cached, prompt(toolResponse = ToolResponseInput("AddSchedule", "{}"))) {
            asked = true; Int.MAX_VALUE
        }
        assertEquals(ConversationDecision.Reuse, decision)
        assertFalse(asked)
    }

    @Test
    fun `같은 세션에서 버릴 때 사유 우선순위는 예산, 시스템 지시, 툴 순`() {
        assertEquals(
            ConversationDecision.Recreate(ConversationResetEvent.Reason.TOKEN_BUDGET),
            decideConversation(cached, prompt(system = "other")) { Int.MAX_VALUE }
        )
        assertEquals(
            ConversationDecision.Recreate(ConversationResetEvent.Reason.SYSTEM_INSTRUCTION),
            decideConversation(cached, prompt(system = "other", tools = emptyList())) { 0 }
        )
        assertEquals(
            ConversationDecision.Recreate(ConversationResetEvent.Reason.TOOLS),
            decideConversation(cached, prompt(tools = emptyList())) { 0 }
        )
    }

    @Test
    fun `세션이 바뀌면 사유 없이 새로 만든다`() {
        assertEquals(ConversationDecision.Recreate(null), decideConversation(cached, prompt(session = "s2")) { 0 })
    }

    @Test
    fun `임계값은 하한과 천장 사이로 묶인다`() {
        assertEquals(Constants.MIN_CONVERSATION_RESET_TOKENS, conversationResetThreshold(1))
        assertEquals(Constants.PREFILL_CEILING_TOKENS, conversationResetThreshold(Int.MAX_VALUE))
    }
}
