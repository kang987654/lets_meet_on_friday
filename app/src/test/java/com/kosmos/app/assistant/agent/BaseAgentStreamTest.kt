package com.kosmos.app.assistant.agent

import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.orchestrator.ChatRequest
import com.kosmos.app.assistant.orchestrator.StreamUpdate
import com.kosmos.app.assistant.tool.ToolExecutor
import com.kosmos.app.assistant.tool.ToolRegistry
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelInfo
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelToolCall
import com.kosmos.app.domain.modelrunner.ModelTurn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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

    /** `logToolCall` 로 넘어간 인자 한 건. */
    private data class ToolCallRecord(val tool: String, val resultJson: String, val note: String?)

    private class Harness(
        val result: AgentResult,
        val updates: List<StreamUpdate>,
        val prompts: List<ChatPrompt>,
        val audit: AuditTrailService,
        val savedMessages: List<ChatMessage>,
        val toolCallLog: List<ToolCallRecord>
    )

    private fun runAgent(
        vararg turns: ModelTurn,
        allowedTools: List<String> = emptyList(),
        toolRegistry: ToolRegistry = mockk(relaxed = true),
        userMessage: String = "질문"
    ): Harness {
        val updates = mutableListOf<StreamUpdate>()
        val runner = ChunkedModelRunner(turns.toList())
        val saved = mutableListOf<ChatMessage>()
        val conversationRepository: ConversationRepository = mockk {
            coEvery { save(any()) } answers {
                saved += firstArg<ChatMessage>()
                AppResult.Success(Unit)
            }
        }
        val audit: AuditTrailService = mockk(relaxed = true)
        // [WHY] `note` 가 nullable 이라 mockk 의 `match`(T : Any) 로는 검증할 수 없다.
        // 넘어온 인자를 그대로 받아 두고 테스트가 값으로 확인한다.
        val toolCallLog = mutableListOf<ToolCallRecord>()
        coEvery { audit.logToolCall(any(), any(), any(), any()) } answers {
            toolCallLog += ToolCallRecord(arg(1), arg(2), arg(3))
        }
        val agent = TestAgent(
            modelRunner = runner,
            toolRegistry = toolRegistry,
            auditTrailService = audit,
            conversationRepository = conversationRepository,
            approvalCoordinator = mockk(relaxed = true),
            allowedTools = allowedTools
        )
        val request = ChatRequest(
            sessionId = "s1",
            message = userMessage,
            onStream = { updates += it }
        )
        val result = runBlocking { agent.execute(request, mockk(relaxed = true)) }
        return Harness(result, updates, runner.receivedPrompts, audit, saved, toolCallLog)
    }

    /** 지정한 JSON 을 돌려주는 executor 하나만 가진 레지스트리. */
    private fun registryWith(name: String, resultJson: String): ToolRegistry {
        val executor: ToolExecutor = mockk {
            every { this@mockk.name } returns name
            every { actionType } returns null
            coEvery { execute(any(), any()) } returns resultJson
        }
        return mockk { every { getExecutor(name) } returns executor }
    }

    private fun toolCall(name: String, args: Map<String, Any> = emptyMap()) =
        ModelToolCall(name, args)

    // --- 감사 기록 (골격 변경 때 끊긴 배선) ---

    @Test
    fun `툴이 실행되면 감사 로그에 TOOL_CALL 이 남는다`() {
        // [WHY] `AuditEventType.TOOL_CALL` 은 enum 과 감사 화면 색상까지 있었지만 생산자가 한
        // 곳도 없었다. 승인이 필요 없는 툴(일정 조회, 웹 검색)은 실행 흔적이 아예 없었다.
        val harness = runAgent(
            ModelTurn("", listOf(toolCall("GetSchedule"))),
            ModelTurn("두 건 있습니다."),
            allowedTools = listOf("GetSchedule"),
            toolRegistry = registryWith("GetSchedule", """{"status":"success"}""")
        )

        assertEquals(
            listOf(ToolCallRecord("GetSchedule", """{"status":"success"}""", null)),
            harness.toolCallLog
        )
    }

    @Test
    fun `무시된 추가 툴 호출이 감사 기록에 남는다`() {
        // [WHY] 첫 호출만 실행하는 것은 의도한 정책이지만, 버려진 사실이 어디에도 없으면
        // "왜 두 번째 일이 안 됐는가"를 추적할 방법이 없다.
        val harness = runAgent(
            ModelTurn("", listOf(toolCall("GetSchedule"), toolCall("AddMemory"))),
            ModelTurn("완료."),
            allowedTools = listOf("GetSchedule"),
            toolRegistry = registryWith("GetSchedule", """{"status":"success"}""")
        )

        val note = harness.toolCallLog.single().note
        assertNotNull("무시된 호출이 기록되지 않았다", note)
        assertTrue("기록에 무시된 툴 이름이 없다: $note", note!!.contains("AddMemory"))
    }

    @Test
    fun `웹 검색이 실행되면 SEARCH_USED 가 기록되고 응답에 표시된다`() {
        // [WHY] 웹 검색은 유일한 네트워크 egress 다. 감사 타입과 포맷터가 있는데 호출하는 곳이
        // 없어, 프라이버시상 가장 기록이 필요한 동작이 감사 로그에 남지 않았다. 또한
        // `searchUsed` 는 항상 false 로 저장돼 사용자가 온디바이스 답변과 구분할 수 없었다.
        val harness = runAgent(
            ModelTurn("", listOf(toolCall("SearchWikipedia", mapOf("topic" to "아폴로 11호")))),
            ModelTurn("1969년입니다."),
            allowedTools = listOf("SearchWikipedia"),
            toolRegistry = registryWith("SearchWikipedia", """{"status":"success","data":"..."}""")
        )

        coVerify { harness.audit.logSearchEvent("s1", "아폴로 11호") }
        assertTrue((harness.result as AgentResult.Text).searchUsed)
        val assistant = harness.savedMessages.last { it.role == ChatMessage.Role.ASSISTANT }
        assertTrue("DB 에 저장된 메시지에도 남아야 한다", assistant.searchUsed)
    }

    @Test
    fun `차단된 웹 검색은 검색으로 기록되지 않는다`() {
        // [WHY] 토글이 꺼져 allowlist 밖이면 실행되지 않았으므로 egress 기록을 남기면 거짓이다.
        // 실행 여부를 결과 JSON 문자열에서 추측하지 않고 값으로 들고 나오는 이유가 이것이다.
        val harness = runAgent(
            ModelTurn("", listOf(toolCall("SearchWikipedia", mapOf("topic" to "아폴로 11호")))),
            ModelTurn("웹 검색이 꺼져 있습니다."),
            allowedTools = emptyList()
        )

        coVerify(exactly = 0) { harness.audit.logSearchEvent(any(), any()) }
        assertFalse((harness.result as AgentResult.Text).searchUsed)
    }

    @Test
    fun `툴을 쓴 턴에서도 감사 로그의 프롬프트가 비어 있지 않다`() {
        // [WHY] 예전에는 `prompt.currentInput` 을 기록했는데 툴 루프가 그것을 ""로 덮어썼다 —
        // **툴을 쓴 대화일수록** 감사 로그의 프롬프트가 비어 있었다.
        val harness = runAgent(
            ModelTurn("", listOf(toolCall("GetSchedule"))),
            ModelTurn("두 건 있습니다."),
            allowedTools = listOf("GetSchedule"),
            toolRegistry = registryWith("GetSchedule", """{"status":"success"}"""),
            userMessage = "오늘 일정 알려줘"
        )

        // [WHY] 선언된 툴 목록도 함께 남긴다 — "툴을 부르지 않았다"가 선언 누락인지 모델의
        // 거부인지 실기기에서 logcat 없이 구분하기 위한 유일한 단서다.
        coVerify {
            harness.audit.logModelRun("s1", "오늘 일정 알려줘", any(), listOf("GetSchedule"))
        }
    }

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

    @Test
    fun `툴 호출이 상한을 넘으면 오류로 끝난다`() {
        // [WHY] 모델이 같은 툴을 계속 부르면 루프가 스스로 멈추지 않는다. 상한이 그 브레이크인데
        // 이 경로만 검증이 없어, 루프 탈출 구조를 건드리는 변경이 무엇을 깨뜨렸는지 알 수 없었다.
        //
        // 턴 3개 = MAX_TOOL_LOOP_COUNT. 세 턴 모두 툴을 부르므로 네 번째 진입에서 걸린다.
        // 하나라도 줄이면 정상 종료로 빠져 이 경로를 못 탄다.
        val harness = runAgent(
            ModelTurn("", listOf(toolCall("GetSchedule"))),
            ModelTurn("", listOf(toolCall("GetSchedule"))),
            ModelTurn("", listOf(toolCall("GetSchedule"))),
            allowedTools = listOf("GetSchedule"),
            toolRegistry = registryWith("GetSchedule", """{"status":"success"}""")
        )

        assertEquals(3, harness.prompts.size)
        val error = (harness.result as AgentResult.Error).error
        assertTrue("ModelInferenceError 가 아니다: $error", error is AppError.ModelInferenceError)
        assertTrue(
            "상한 초과 문구가 없다: $error",
            (error as AppError.ModelInferenceError).reason.contains("툴 호출 반복 상한")
        )
        coVerify { harness.audit.logError("s1", "Max tool loop count exceeded") }
        // [WHY] 이 경로는 handleErrorAndReturn 을 거치지 않아 말풍선을 남기지 않는다. 다른 오류
        // 경로(추론 실패)와 갈리는 지점이므로 현재 동작을 고정해 둔다.
        assertTrue(
            "말풍선이 저장되면 안 된다: ${harness.savedMessages}",
            harness.savedMessages.none { it.role == ChatMessage.Role.ASSISTANT }
        )
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
