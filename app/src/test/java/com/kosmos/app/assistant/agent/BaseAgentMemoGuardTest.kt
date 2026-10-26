package com.kosmos.app.assistant.agent

import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.orchestrator.ChatRequest
import com.kosmos.app.assistant.tool.ToolArguments
import com.kosmos.app.assistant.tool.ToolExecutor
import com.kosmos.app.assistant.tool.ToolRegistry
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.security.ApprovalRules
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.MemoSaveIntent
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelToolCall
import com.kosmos.app.domain.modelrunner.ModelTurn
import com.kosmos.app.testing.ScriptedModelRunner
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 사실 저장 가드(0.38.9 M1) — 저장 낱말 없는 메모 저장은 막고 먼저 묻게 하며, 저장 물음에 "응"이라 했는데 모델이 저장을 부르지 않으면
 * 앱이 저장 시트를 연다. 저장하지 않은 턴의 "기억해 둘게요"는 물음으로 바뀐다.
 */
@RunWith(RobolectricTestRunner::class)
class BaseAgentMemoGuardTest {

    private class TestAgent(
        modelRunner: ModelRunner,
        toolRegistry: ToolRegistry,
        auditTrailService: AuditTrailService,
        conversationRepository: ConversationRepository,
        approvalCoordinator: ApprovalCoordinator
    ) : BaseAgent(modelRunner, toolRegistry, auditTrailService, conversationRepository, approvalCoordinator) {
        override suspend fun execute(request: ChatRequest, context: ContextBuilder.Context): AgentResult =
            executeToolLoop(request, ChatPrompt(sessionId = "s1", systemInstruction = "test", history = emptyList(), currentInput = request.message), TOOLS)

        override fun availableTools(context: ContextBuilder.Context): List<String> = TOOLS
    }

    private class Run(val reply: String, val prompts: List<ChatPrompt>, val executed: List<String>)

    private fun msg(role: ChatMessage.Role, content: String, at: Long) = ChatMessage("$at", "s1", role, content, InputType.TEXT, createdAt = at)

    private fun run(
        userMessage: String,
        history: List<ChatMessage>,
        vararg turns: ModelTurn,
        approve: Boolean = true
    ): Run {
        val executed = mutableListOf<String>()
        val executor: ToolExecutor = mockk {
            every { name } returns "AddMemory"
            every { actionType } returns ApprovalRules.ActionType.MEMORY_WRITE
            every { buildApprovalRequest(any(), any()) } returns mockk(relaxed = true)
            coEvery { execute(any(), any()) } answers {
                executed += firstArg<ToolArguments>().optString("content").orEmpty()
                """{"status":"success","message":"기억에 저장했어요."}"""
            }
        }
        val registry: ToolRegistry = mockk { every { getExecutor("AddMemory") } returns executor }
        val repository: ConversationRepository = mockk {
            coEvery { save(any()) } returns AppResult.Success(Unit)
            coEvery { getRecentBySession("s1", any()) } returns AppResult.Success(history + msg(ChatMessage.Role.USER, userMessage, 99))
        }
        val approval: ApprovalCoordinator = mockk { coEvery { requireApproval(any()) } returns approve }
        val runner = ScriptedModelRunner(turns.toList())
        val agent = TestAgent(runner, registry, mockk(relaxed = true), repository, approval)
        val result = runBlocking { agent.execute(ChatRequest(sessionId = "s1", message = userMessage), mockk(relaxed = true)) }
        return Run((result as AgentResult.Text).content, runner.receivedPrompts, executed)
    }

    private fun saveCall(content: String) = ModelToolCall("AddMemory", mapOf("content" to content))

    @Test
    fun `사실만 말한 턴의 메모 저장은 실행하지 않고 먼저 물으라는 결과를 돌려준다`() {
        val r = run(
            "내 사물함 번호는 214번이야", emptyList(),
            ModelTurn("", listOf(saveCall("사물함 번호 214번"))),
            ModelTurn("사물함 번호가 214번이시군요. 메모해 둘까요?")
        )

        assertEquals(emptyList<String>(), r.executed)
        assertTrue(r.prompts[1].toolResponse.toString().contains("메모해 둘까요"))
        assertEquals("사물함 번호가 214번이시군요. 메모해 둘까요?", r.reply)
    }

    @Test
    fun `저장 낱말이 있거나 앞에서 물어 둔 뒤의 응이면 그대로 저장한다`() {
        val asked = listOf(msg(ChatMessage.Role.USER, "내 사물함 번호는 214번이야", 1), msg(ChatMessage.Role.ASSISTANT, "메모해 둘까요?", 2))
        val byWord = run("내 사물함 번호는 214번이야 기억해줘", emptyList(), ModelTurn("", listOf(saveCall("사물함 번호 214번"))), ModelTurn("메모했어요."))
        val byYes = run("응", asked, ModelTurn("", listOf(saveCall("사물함 번호 214번"))), ModelTurn("메모했어요."))

        assertEquals(listOf("사물함 번호 214번"), byWord.executed)
        assertEquals(listOf("사물함 번호 214번"), byYes.executed)
    }

    @Test
    fun `물음 뒤 응에 모델이 저장을 부르지 않으면 앱이 저장 시트를 연다 — 막아 둔 메모가 있으면 그 문장으로`() {
        val asked = listOf(msg(ChatMessage.Role.USER, "내 사물함 번호는 214번이야", 1), msg(ChatMessage.Role.ASSISTANT, "메모해 둘까요?", 2))
        val agentRuns = run("응", asked, ModelTurn("네, 메모해 두었어요."))

        assertEquals(listOf("내 사물함 번호는 214번이야"), agentRuns.executed)
        assertEquals("메모에 저장했어요.", agentRuns.reply)

        val rejected = run("응", asked, ModelTurn("네, 메모해 두었어요."), approve = false)
        assertEquals("메모에는 남기지 않았어요.", rejected.reply)
    }

    @Test
    fun `가드가 막은 메모는 다음 턴의 응에서 모델이 쓴 문장 그대로 저장 시트에 오른다`() {
        val executed = mutableListOf<String>()
        val executor: ToolExecutor = mockk {
            every { name } returns "AddMemory"
            every { actionType } returns ApprovalRules.ActionType.MEMORY_WRITE
            every { buildApprovalRequest(any(), any()) } returns mockk(relaxed = true)
            coEvery { execute(any(), any()) } answers {
                executed += firstArg<ToolArguments>().optString("content").orEmpty()
                """{"status":"success"}"""
            }
        }
        val log = mutableListOf(msg(ChatMessage.Role.USER, "내 사물함 번호는 214번이야", 1))
        val repository: ConversationRepository = mockk {
            coEvery { save(any()) } answers {
                log += firstArg<ChatMessage>().copy(createdAt = log.size + 1L)
                AppResult.Success(Unit)
            }
            coEvery { getRecentBySession("s1", any()) } answers { AppResult.Success(log.toList()) }
        }
        val agent = TestAgent(
            ScriptedModelRunner(
                listOf(
                    ModelTurn("", listOf(saveCall("사물함 번호 214번"))),
                    ModelTurn("사물함 번호가 214번이시군요. 메모해 둘까요?"),
                    ModelTurn("네, 메모해 두었어요.")
                )
            ),
            mockk { every { getExecutor("AddMemory") } returns executor },
            mockk(relaxed = true),
            repository,
            mockk { coEvery { requireApproval(any()) } returns true }
        )

        runBlocking { agent.execute(ChatRequest(sessionId = "s1", message = "내 사물함 번호는 214번이야"), mockk(relaxed = true)) }
        log += msg(ChatMessage.Role.USER, "응", 100)
        val second = runBlocking { agent.execute(ChatRequest(sessionId = "s1", message = "응"), mockk(relaxed = true)) }

        assertEquals(listOf("사물함 번호 214번"), executed)
        assertEquals("메모에 저장했어요.", (second as AgentResult.Text).content)
    }

    @Test
    fun `저장하지 않은 턴의 기억해 둘게요는 물음으로 바뀐다`() {
        val r = run("우리 강아지 이름은 콩이야", emptyList(), ModelTurn("콩이라는 이름을 기억해 둘게요! 😊"))

        assertEquals(MemoSaveIntent.ASK, r.reply)
        assertEquals(emptyList<String>(), r.executed)
    }

    private companion object {
        val TOOLS = listOf("AddMemory")
    }
}
