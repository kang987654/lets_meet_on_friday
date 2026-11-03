package com.kosmos.app.assistant.agent

import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.context.PromptAssembler
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.ChatRequest
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelTurn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    // [WHY] 이것이 라우터 폐지의 핵심 계약이다. 예전 키워드 분류는 `예약`은 잡고 `약속`은
    // 놓치는 식으로 새서, 표현에 따라 툴이 사라졌다.
    @Test
    fun `웹 검색이 꺼져 있으면 로컬 툴 6개만 노출된다`() {
        // [WHY] SearchMemory 는 로컬 조회이므로 토글과 무관하게 항상 있다. 매 턴 자동 RAG
        // 주입을 없애고 기억 조회를 툴로 옮긴 결과다 (ADR-013).
        // AddReminder(B1, 0.21.0)도 로컬 동작이라 상시 노출이다 — 기대 목록 확장은 계약
        // 확장이며 CHANGELOG 에 기록했다.
        val tools = agent().availableTools(context(webSearchEnabled = false))

        assertEquals(listOf("AddSchedule", "GetSchedule", "AddMemory", "SearchMemory", "AddReminder", "ListReminders"), tools)
    }

    // [WHY] 문서 블록은 DB 기록에만 저장된다 — 재사용된 대화에는 이번 입력만 가서 모델이 문서를 못 봤다(0.40.8, 에뮬레이터 재현).
    @Test
    fun `문서가 첨부된 턴은 대화를 다시 만들고 보통 턴은 재사용한다`() {
        val runner: ModelRunner = mockk(relaxed = true) {
            coEvery { generate(any(), any()) } returns AppResult.Success(ModelTurn("네"))
        }
        val agent = KosmosAgent(
            modelRunner = runner,
            toolRegistry = mockk(relaxed = true),
            auditTrailService = mockk(relaxed = true),
            conversationRepository = mockk(relaxed = true) { coEvery { save(any()) } returns AppResult.Success(Unit) },
            approvalCoordinator = mockk(relaxed = true),
            promptAssembler = PromptAssembler()
        )

        runBlocking { agent.execute(ChatRequest(sessionId = "s1", message = "안녕"), context(webSearchEnabled = false)) }
        coVerify(exactly = 0) { runner.invalidateConversation() }

        runBlocking {
            agent.execute(
                ChatRequest(sessionId = "s1", message = "코드명이 뭐야?", documentText = "프로젝트 코드명은 푸른고래입니다."),
                context(webSearchEnabled = false)
            )
        }
        coVerifyOrder {
            runner.invalidateConversation()
            runner.generate(any(), any())
        }
    }

    @Test
    fun `웹 검색을 켜면 위키 툴이 더해진다`() {
        val tools = agent().availableTools(context(webSearchEnabled = true))

        assertEquals(
            listOf("AddSchedule", "GetSchedule", "AddMemory", "SearchMemory", "AddReminder", "ListReminders", "SearchWikipedia"),
            tools
        )
    }
}
