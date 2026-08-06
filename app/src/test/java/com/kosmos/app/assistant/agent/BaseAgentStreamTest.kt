package com.kosmos.app.assistant.agent

import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.orchestrator.ChatRequest
import com.kosmos.app.assistant.orchestrator.StreamUpdate
import com.kosmos.app.assistant.tool.ToolRegistry
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelInfo
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelToolCall
import com.kosmos.app.domain.modelrunner.ModelTurn
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [BaseAgentStreamTest]
 * 툴 루프에서 스트리밍 상태가 턴 경계를 지키는지, 구조화된 툴 호출이 실행 경로로 이어지는지
 * 검증합니다.
 *
 * [WHY] fake 는 응답을 **토큰 단위로 쪼개** emit 한다. 기존 통합 테스트의 fake 들은 응답 전체를
 * `onToken` 한 번으로 넘기므로 스트리밍 결함을 재현할 수 없다.
 *
 * [WHY] `allowedTools` 를 비워 두면 툴 호출이 allowlist 검증에서 차단되고 오류 JSON 이
 * `toolResponse` 로 되돌아가면서 루프가 2턴으로 넘어간다. `ToolRegistry` 를 채우지 않고도
 * 다중 턴 시나리오를 만들 수 있는 가장 좁은 경로다.
 */
@RunWith(RobolectricTestRunner::class)
class BaseAgentStreamTest {

    /** 턴마다 다른 [ModelTurn] 을 주고, 텍스트는 토큰 단위로 쪼개 emit 하는 fake. */
    private class ChunkedModelRunner(private val turns: List<ModelTurn>) : ModelRunner {
        private var index = 0
        val receivedPrompts = mutableListOf<ChatPrompt>()

        override val loadState: StateFlow<ModelLoadState> = MutableStateFlow(
            ModelLoadState.Ready(ModelInfo("fake", "fake", "1.0", "int8", 0L))
        )

        override suspend fun generate(prompt: ChatPrompt, onToken: ((String) -> Unit)?): AppResult<ModelTurn> {
            receivedPrompts += prompt
            val turn = turns.getOrElse(index) { ModelTurn("") }
            index++
            turn.text.chunked(3).forEach { onToken?.invoke(it) }
            return AppResult.Success(turn)
        }

        override suspend fun generateWithImage(
            prompt: ChatPrompt,
            imageBytes: ByteArray,
            imageTokenBudget: Int,
            onToken: ((String) -> Unit)?
        ): AppResult<ModelTurn> = generate(prompt, onToken)

        override suspend fun generateWithAudio(
            prompt: ChatPrompt,
            audioPath: String,
            onToken: ((String) -> Unit)?
        ): AppResult<ModelTurn> = generate(prompt, onToken)

        override suspend fun cancel() = Unit
        override suspend fun warmUp() = Unit
        override fun close() = Unit
    }

    private class TestAgent(
        modelRunner: ModelRunner,
        toolRegistry: ToolRegistry,
        auditTrailService: AuditTrailService,
        conversationRepository: ConversationRepository,
        approvalCoordinator: ApprovalCoordinator,
        private val allowedTools: List<String> = emptyList()
    ) : BaseAgent(modelRunner, toolRegistry, auditTrailService, conversationRepository, approvalCoordinator) {

        override suspend fun execute(request: ChatRequest, context: ContextBuilder.Context): AgentResult =
            executeToolLoop(request, prompt(), allowedTools = allowedTools)

        override fun availableTools(context: ContextBuilder.Context): List<String> = allowedTools

        private fun prompt() = ChatPrompt(
            sessionId = "s1",
            systemInstruction = "test",
            history = emptyList(),
            currentInput = "hi"
        )
    }

    private class Harness(
        val result: AgentResult,
        val updates: List<StreamUpdate>,
        val prompts: List<ChatPrompt>
    )

    private fun runAgent(
        vararg turns: ModelTurn,
        allowedTools: List<String> = emptyList()
    ): Harness {
        val updates = mutableListOf<StreamUpdate>()
        val runner = ChunkedModelRunner(turns.toList())
        val conversationRepository: ConversationRepository = mockk {
            coEvery { save(any()) } returns AppResult.Success(Unit)
        }
        val agent = TestAgent(
            modelRunner = runner,
            toolRegistry = mockk(relaxed = true),
            auditTrailService = mockk(relaxed = true),
            conversationRepository = conversationRepository,
            approvalCoordinator = mockk(relaxed = true),
            allowedTools = allowedTools
        )
        val request = ChatRequest(
            sessionId = "s1",
            message = "질문",
            onStream = { updates += it }
        )
        val result = runBlocking { agent.execute(request, mockk(relaxed = true)) }
        return Harness(result, updates, runner.receivedPrompts)
    }

    private fun toolCall(name: String) = ModelToolCall(name, emptyMap())

    // --- 턴 경계 ---

    @Test
    fun `툴 루프 2턴에서 1턴 문장이 2턴에 이어붙지 않는다`() {
        // [WHY] 이 테스트가 ADR-007 의 핵심 회귀 방지다. UI 가 직접 누적하던 시절에는
        // 턴 경계 신호가 없어 1턴 문장이 2턴 문장 앞에 그대로 남았다.
        val harness = runAgent(
            ModelTurn("확인해보겠습니다.", listOf(toolCall("GetSchedule"))),
            ModelTurn("오늘 일정은 두 건입니다.")
        )

        val last = harness.updates.last().content
        assertEquals("오늘 일정은 두 건입니다.", last)
        assertFalse("1턴 문장이 남았다: $last", last!!.contains("확인해보겠습니다"))
    }

    @Test
    fun `최종 스트리밍 내용이 커밋되는 텍스트와 같다`() {
        // [WHY] 예전에는 스트리밍이 누적본을 보여주고 커밋은 마지막 턴만 저장해, 완료 순간
        // 텍스트가 줄어드는 것처럼 보였다.
        val harness = runAgent(
            ModelTurn("잠시만요.", listOf(toolCall("GetSchedule"))),
            ModelTurn("두 건 있습니다.")
        )

        assertTrue(harness.result is AgentResult.Text)
        assertEquals((harness.result as AgentResult.Text).content, harness.updates.last().content)
    }

    // --- 구조화된 툴 호출 배선 ---

    @Test
    fun `툴 호출이 있으면 다음 턴에 toolResponse 로 회신된다`() {
        // [WHY] 이전에는 `<tool_response>` 텍스트를 사용자 턴으로 위장해 보냈다. 런타임의
        // 함수호출 경로에서는 전용 응답 타입이어야 모델 템플릿의 역할과 맞는다 (ADR-008).
        val harness = runAgent(
            ModelTurn("", listOf(toolCall("GetSchedule"))),
            ModelTurn("완료했습니다.")
        )

        assertEquals(2, harness.prompts.size)
        val second = harness.prompts[1]
        assertNotNull("두 번째 턴이 toolResponse 로 가야 한다", second.toolResponse)
        assertEquals("GetSchedule", second.toolResponse!!.name)
        assertEquals("", second.currentInput)
    }

    @Test
    fun `툴 이름은 프롬프트의 enabledTools 로 전달된다`() {
        // [WHY] 런타임이 이 목록으로 모델에게 툴을 선언한다. 비면 모델은 툴이 없다고 본다.
        // 빈 리스트끼리 비교하면 BaseAgent 가 enabledTools 를 채우지 않아도 통과하는 공허한
        // 검증이 되므로, 반드시 비어 있지 않은 목록으로 계약을 고정한다.
        val harness = runAgent(
            ModelTurn("안녕하세요."),
            allowedTools = listOf("AddSchedule", "AddMemory")
        )

        assertEquals(listOf("AddSchedule", "AddMemory"), harness.prompts.first().enabledTools)
    }

    @Test
    fun `텍스트 없이 툴 호출만 온 턴도 처리된다`() {
        // [WHY] 모델이 말 없이 곧바로 툴을 부르는 경우가 흔하다.
        val harness = runAgent(
            ModelTurn("", listOf(toolCall("AddMemory"))),
            ModelTurn("기억했습니다.")
        )

        assertEquals("기억했습니다.", (harness.result as AgentResult.Text).content)
    }

    @Test
    fun `툴 호출이 없으면 한 턴으로 끝난다`() {
        val harness = runAgent(ModelTurn("안녕하세요. 무엇을 도와드릴까요?"))

        assertEquals(1, harness.prompts.size)
        assertEquals("안녕하세요. 무엇을 도와드릴까요?", (harness.result as AgentResult.Text).content)
    }

    // --- 스트리밍 표시 ---

    @Test
    fun `생각 블록만 스트리밍되는 구간에서는 content 가 null 이다`() {
        // [WHY] 빈 문자열을 넘기면 UI 가 "텍스트 있음"으로 오해해 타이핑 인디케이터를 숨긴다.
        val harness = runAgent(ModelTurn("<|think|>고민 중입니다</|think|>답입니다."))

        val thinkingOnly = harness.updates.filter { it.thinking != null && it.content == null }
        assertTrue("생각만 있는 구간이 없었다", thinkingOnly.isNotEmpty())
        assertEquals("답입니다.", harness.updates.last().content)
    }

    @Test
    fun `스트리밍 내용이 턴 안에서 역행하지 않는다`() {
        val harness = runAgent(ModelTurn("오늘 일정은 두 건입니다. 회의와 점심이에요."))

        var previous = 0
        harness.updates.forEach { update ->
            val length = update.content?.length ?: 0
            assertTrue("본문이 역행했다", length >= previous)
            previous = length
        }
    }

    @Test
    fun `모델이 tool_call 문법을 텍스트로 흘려도 화면에 노출되지 않는다`() {
        // [WHY] 네이티브 경로에서는 나오지 않아야 하지만, 옛 프롬프트를 학습한 모델이 흉내낼
        // 수 있다. 표시 단계의 방어는 싸므로 유지한다.
        val harness = runAgent(ModelTurn("처리합니다.<tool_call>{\"name\":\"GetSchedule\"}</tool_call>"))

        harness.updates.forEach { update ->
            val content = update.content
            if (content != null) {
                assertFalse("노출됨: $content", content.contains("<tool"))
                assertFalse("노출됨: $content", content.contains("\"name\""))
            }
        }
    }
}
