package com.kosmos.app.assistant.agent

import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.context.PromptAssembler
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [KosmosAgentTest]
 * 단일 에이전트가 모든 툴을 노출하는지, 웹 검색만 토글로 게이트되는지 검증합니다.
 *
 * [WHY] 이전에는 `IntentClassifier` 가 에이전트를 고르고 **에이전트마다 툴 목록이 달랐다.**
 * 분류가 틀리면 툴이 프롬프트에서 사라졌고, 실기기에서 "내일 3시에 치과 예약 잡아줘"가
 * 캘린더로 가지 못해 일정 등록이 조용히 실패했다. 라우터를 폐지했으므로 이 테스트가 새 계약을
 * 고정한다 — **어떤 입력이든 일정·메모리 툴이 항상 있어야 한다** (ADR-008).
 */
class KosmosAgentTest {

    private fun agent() = KosmosAgent(
        modelRunner = mockk(relaxed = true),
        toolRegistry = mockk(relaxed = true),
        auditTrailService = mockk(relaxed = true),
        conversationRepository = mockk(relaxed = true),
        approvalCoordinator = mockk(relaxed = true),
        promptAssembler = PromptAssembler()
    )

    private fun context(webSearchEnabled: Boolean) = ContextBuilder.Context(
        sessionId = "s1",
        recentConversations = emptyList(),
        responseStyle = "DEFAULT",
        webSearchEnabled = webSearchEnabled
    )

    @Test
    fun `웹 검색이 꺼져 있으면 로컬 툴 4개만 노출된다`() {
        // [WHY] SearchMemory 는 로컬 조회이므로 토글과 무관하게 항상 있다. 매 턴 자동 RAG
        // 주입을 없애고 기억 조회를 툴로 옮긴 결과다 (ADR-013).
        val tools = agent().availableTools(context(webSearchEnabled = false))

        assertEquals(listOf("AddSchedule", "GetSchedule", "AddMemory", "SearchMemory"), tools)
    }

    @Test
    fun `웹 검색을 켜면 위키 툴이 더해진다`() {
        val tools = agent().availableTools(context(webSearchEnabled = true))

        assertEquals(
            listOf("AddSchedule", "GetSchedule", "AddMemory", "SearchMemory", "SearchWikipedia"),
            tools
        )
    }

    @Test
    fun `일정 툴은 입력 표현과 무관하게 항상 노출된다`() {
        // [WHY] 이것이 라우터 폐지의 핵심 계약이다. 예전 키워드 분류는 `예약`은 잡고 `약속`은
        // 놓치는 식으로 새서, 표현에 따라 툴이 사라졌다.
        val tools = agent().availableTools(context(webSearchEnabled = false))

        assertTrue(tools.contains("AddSchedule"))
        assertTrue(tools.contains("GetSchedule"))
    }
}
