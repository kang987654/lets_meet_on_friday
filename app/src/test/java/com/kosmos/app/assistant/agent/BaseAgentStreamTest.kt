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
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [BaseAgentStreamTest]
 * 툴 루프에서 스트리밍 상태가 턴 경계를 지키는지 검증합니다.
 *
 * [WHY] `BaseAgent` 테스트가 **아예 없었다**. 기존 통합 테스트의 fake 들은 응답 전체를
 * `onToken` 한 번으로 넘기므로 스트리밍 결함을 재현할 수 없다 — 여기의 fake 는 응답을
 * **토큰 단위로 쪼개** emit 해야 한다.
 *
 * [WHY] `allowedTools` 를 비워 두면 툴 콜이 allowlist 검증에서 차단되고 오류 JSON 이
 * 되돌아가면서 루프가 2턴으로 넘어간다. `ToolRegistry` 를 채우지 않고도 다중 턴 시나리오를
 * 만들 수 있는 가장 좁은 경로다.
 */
@RunWith(RobolectricTestRunner::class)
class BaseAgentStreamTest {

    /** 응답을 토큰 단위로 쪼개 emit 하는 fake. 턴마다 다른 응답을 준다. */
    private class ChunkedModelRunner(private val responses: List<String>) : ModelRunner {
        private var turn = 0

        override val loadState: StateFlow<ModelLoadState> = MutableStateFlow(
            ModelLoadState.Ready(ModelInfo("fake", "fake", "1.0", "int8", 0L))
        )

        override suspend fun generate(prompt: ChatPrompt, onToken: ((String) -> Unit)?): AppResult<String> {
            val response = responses.getOrElse(turn) { "" }
            turn++
            response.chunked(3).forEach { onToken?.invoke(it) }
            return AppResult.Success(response)
        }

        override suspend fun generateWithImage(
            prompt: ChatPrompt,
            imageBytes: ByteArray,
            imageTokenBudget: Int,
            onToken: ((String) -> Unit)?
        ): AppResult<String> = generate(prompt, onToken)

        override suspend fun generateWithAudio(
            prompt: ChatPrompt,
            audioPath: String,
            onToken: ((String) -> Unit)?
        ): AppResult<String> = generate(prompt, onToken)

        override suspend fun cancel() = Unit
        override suspend fun warmUp() = Unit
        override fun close() = Unit
    }

    private class TestAgent(
        modelRunner: ModelRunner,
        toolRegistry: ToolRegistry,
        auditTrailService: AuditTrailService,
        conversationRepository: ConversationRepository,
        approvalCoordinator: ApprovalCoordinator
    ) : BaseAgent(modelRunner, toolRegistry, auditTrailService, conversationRepository, approvalCoordinator) {

        override suspend fun execute(request: ChatRequest, context: ContextBuilder.Context): AgentResult =
            executeToolLoop(request, prompt(), allowedTools = emptyList())

        override fun availableTools(context: ContextBuilder.Context): List<String> = emptyList()

        private fun prompt() = ChatPrompt(
            sessionId = "s1",
            systemInstruction = "test",
            history = emptyList(),
            currentInput = "hi"
        )
    }

    private fun runAgent(responses: List<String>): Pair<AgentResult, List<StreamUpdate>> {
        val updates = mutableListOf<StreamUpdate>()
        val auditTrailService: AuditTrailService = mockk(relaxed = true)
        val conversationRepository: ConversationRepository = mockk {
            coEvery { save(any()) } returns AppResult.Success(Unit)
        }
        val agent = TestAgent(
            modelRunner = ChunkedModelRunner(responses),
            toolRegistry = mockk(relaxed = true),
            auditTrailService = auditTrailService,
            conversationRepository = conversationRepository,
            approvalCoordinator = mockk(relaxed = true)
        )
        val request = ChatRequest(
            sessionId = "s1",
            message = "질문",
            onStream = { updates += it }
        )
        val result = runBlocking { agent.execute(request, mockk(relaxed = true)) }
        return result to updates
    }

    @Test
    fun `툴 루프 2턴에서 1턴 문장이 2턴에 이어붙지 않는다`() {
        // [WHY] 이 테스트가 이번 수정의 핵심 회귀 방지다. UI 가 직접 누적하던 시절에는
        // 턴 경계 신호가 없어 1턴 문장이 2턴 문장 앞에 그대로 남았다.
        val (_, updates) = runAgent(
            listOf(
                "확인해보겠습니다.<tool_call>{\"name\":\"GetSchedule\", \"args\":{}}</tool_call>",
                "오늘 일정은 두 건입니다."
            )
        )

        val last = updates.last().content
        assertEquals("오늘 일정은 두 건입니다.", last)
        assertFalse("1턴 문장이 남았다: $last", last!!.contains("확인해보겠습니다"))
    }

    @Test
    fun `최종 스트리밍 내용이 커밋되는 텍스트와 같다`() {
        // [WHY] 예전에는 스트리밍이 누적본을 보여주고 커밋은 마지막 턴만 저장해, 완료 순간
        // 텍스트가 줄어드는 것처럼 보였다.
        val (result, updates) = runAgent(
            listOf(
                "잠시만요.<tool_call>{\"name\":\"GetSchedule\", \"args\":{}}</tool_call>",
                "두 건 있습니다."
            )
        )

        assertTrue(result is AgentResult.Text)
        assertEquals((result as AgentResult.Text).content, updates.last().content)
    }

    @Test
    fun `스트리밍 중 어떤 시점에도 프로토콜 문법이 노출되지 않는다`() {
        val (_, updates) = runAgent(
            listOf(
                "처리합니다.<tool_call>{\"name\":\"GetSchedule\", \"args\":{}}</tool_call>",
                "완료했습니다."
            )
        )

        updates.forEach { update ->
            val content = update.content
            if (content != null) {
                assertFalse("노출됨: $content", content.contains("<tool"))
                assertFalse("노출됨: $content", content.contains("\"name\""))
            }
        }
    }

    @Test
    fun `생각 블록만 스트리밍되는 구간에서는 content 가 null 이다`() {
        // [WHY] 빈 문자열을 넘기면 UI 가 "텍스트 있음"으로 오해해 타이핑 인디케이터를 숨긴다.
        val (_, updates) = runAgent(listOf("<|think|>고민 중입니다</|think|>답입니다."))

        val thinkingOnly = updates.filter { it.thinking != null && it.content == null }
        assertTrue("생각만 있는 구간이 없었다", thinkingOnly.isNotEmpty())
        assertEquals("답입니다.", updates.last().content)
    }

    @Test
    fun `태그가 없는 단순 응답도 그대로 스트리밍된다`() {
        val (result, updates) = runAgent(listOf("안녕하세요. 무엇을 도와드릴까요?"))

        assertEquals("안녕하세요. 무엇을 도와드릴까요?", updates.last().content)
        assertEquals("안녕하세요. 무엇을 도와드릴까요?", (result as AgentResult.Text).content)
    }

    @Test
    fun `스트리밍 내용이 턴 안에서 역행하지 않는다`() {
        val (_, updates) = runAgent(listOf("오늘 일정은 두 건입니다. 회의와 점심이에요."))

        var previous = 0
        updates.forEach { update ->
            val length = update.content?.length ?: 0
            assertTrue("본문이 역행했다", length >= previous)
            previous = length
        }
    }
}
