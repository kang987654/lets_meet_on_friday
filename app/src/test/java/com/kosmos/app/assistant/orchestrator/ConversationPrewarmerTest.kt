package com.kosmos.app.assistant.orchestrator

import com.kosmos.app.assistant.agent.KosmosAgent
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.context.PromptAssembler
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelTurn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.kosmos.app.domain.agent.ChatRequest

/**
 * 답 직후·엔진 Ready 직후 대화 미리 준비(0.39.2 M3·0.39.4 M2) — 다음 턴과 같은 캐시 키로 준비하는가, 새 요청이 오면 엔진에 닿기 전의 준비를 멈추는가.
 * 실제로 다시 만들지·프리필할지는 네이티브 런너 몫이라 실험실(exp66d·e)과 회귀 세트 `--prewarm` 이 본다.
 */
@RunWith(RobolectricTestRunner::class)
class ConversationPrewarmerTest {

    private fun msg(role: ChatMessage.Role, content: String, at: Long) = ChatMessage("$at", "s1", role, content, InputType.TEXT, createdAt = at)

    private val history = listOf(
        msg(ChatMessage.Role.USER, "아침 운동 루틴 짜줘", 1),
        msg(ChatMessage.Role.ASSISTANT, "죄송해요, 저는 운동 루틴을 짜드리는 기능은 없어요.", 2),
        msg(ChatMessage.Role.USER, "왜 안 된다고 했어?", 3),
        msg(ChatMessage.Role.ASSISTANT, "운동 루틴 짜기는 제가 가진 툴로 할 수 없어서 그렇게 말씀드렸어요.", 4)
    )

    private fun context(messages: List<ChatMessage>) =
        ContextBuilder.Context(recentConversations = messages, sessionId = "s1", responseStyle = "DEFAULT")

    private val defaultSessionStore: SessionStore = mockk(relaxed = true) {
        every { activeSessionIdFlow } returns flowOf("s1")
    }

    private fun agent(runner: ModelRunner) = KosmosAgent(
        modelRunner = runner,
        toolRegistry = mockk(relaxed = true),
        auditTrailService = mockk(relaxed = true),
        conversationRepository = mockk(relaxed = true) { coEvery { save(any()) } returns AppResult.Success(Unit) },
        approvalCoordinator = mockk(relaxed = true),
        promptAssembler = PromptAssembler()
    )

    @Test
    fun `미리 준비하는 대화는 다음 턴과 지시·툴이 같고 거절 턴은 빠진다`() {
        val prepared = mutableListOf<ChatPrompt>()
        val generated = mutableListOf<ChatPrompt>()
        val runner: ModelRunner = mockk(relaxed = true) {
            coEvery { prepareConversation(capture(prepared)) } returns Unit
            coEvery { generate(capture(generated), any()) } returns AppResult.Success(ModelTurn("좋은 아침이에요."))
        }
        val agent = agent(runner)

        runBlocking {
            agent.prepareNextTurn(context(history))
            val next = history + msg(ChatMessage.Role.USER, "좋은 아침", 5)
            assertTrue(agent.execute(ChatRequest(sessionId = "s1", message = "좋은 아침"), context(next)) is AgentResult.Text)
        }

        assertEquals(generated.first().systemInstruction, prepared.single().systemInstruction)
        assertEquals(generated.first().enabledTools, prepared.single().enabledTools)
        assertEquals(listOf("3", "4"), prepared.single().history.map { it.id })
    }

    @Test
    fun `새 요청이 오면 아직 엔진에 닿지 않은 준비는 멈춘다`() {
        val gate = CompletableDeferred<Unit>()
        val builder: ContextBuilder = mockk {
            coEvery { build("s1") } coAnswers {
                gate.await()
                AppResult.Success(context(history))
            }
        }
        val runner: ModelRunner = mockk(relaxed = true)
        val prewarmer = ConversationPrewarmer(builder, runner, defaultSessionStore) { agent(runner) }

        prewarmer.afterTurn("s1")
        prewarmer.onUserActivity()
        gate.complete(Unit)
        Thread.sleep(200)

        coVerify(exactly = 0) { runner.prepareConversation(any()) }
    }

    @Test
    fun `턴이 끝나면 다음 턴의 대화를 준비한다`() {
        val done = CompletableDeferred<ChatPrompt>()
        val builder: ContextBuilder = mockk { coEvery { build("s1") } returns AppResult.Success(context(history.take(2))) }
        val runner: ModelRunner = mockk(relaxed = true) {
            coEvery { prepareConversation(any()) } coAnswers { done.complete(firstArg()); Unit }
        }

        ConversationPrewarmer(builder, runner, defaultSessionStore) { agent(runner) }.apply { quietMs = 0 }.afterTurn("s1")

        val prompt = runBlocking { withTimeout(5_000) { done.await() } }
        assertEquals("s1", prompt.sessionId)
    }

    @Test
    fun `엔진이 준비되면 활성 대화방의 대화를 미리 준비하고 복귀로 다시 Ready 가 돼도 매번 준비한다`() {
        val loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Loading)
        val prepared = java.util.concurrent.CopyOnWriteArrayList<ChatPrompt>()
        val builder: ContextBuilder = mockk { coEvery { build("s1") } returns AppResult.Success(context(history.take(2))) }
        val runner: ModelRunner = mockk<ModelRunner>(relaxed = true).also {
            every { it.loadState } returns (loadState as StateFlow<ModelLoadState>)
            coEvery { it.prepareConversation(any()) } coAnswers { prepared += firstArg<ChatPrompt>() }
        }
        val prewarmer = ConversationPrewarmer(builder, runner, defaultSessionStore) { agent(runner) }.also {
            it.quietMs = 0
            it.start()
        }

        // 첫 실행 Ready
        loadState.value = ModelLoadState.Ready(mockk(relaxed = true))
        runBlocking {
            withTimeout(5_000) { while (prepared.size < 1) kotlinx.coroutines.delay(20) }
        }
        assertEquals(1, prepared.size)
        assertEquals("s1", prepared[0].sessionId)

        // 백그라운드 해제 후 복귀 Ready
        loadState.value = ModelLoadState.Loading
        loadState.value = ModelLoadState.Ready(mockk(relaxed = true))
        runBlocking {
            withTimeout(5_000) { while (prepared.size < 2) kotlinx.coroutines.delay(20) }
        }
        assertEquals(2, prepared.size)
        prewarmer.onUserActivity()
    }

    @Test
    fun `조용해지기까지 남은 시간은 마지막 활동 기준이고 지났으면 0이다`() {
        assertEquals(5_000L, quietRemainingMs(lastActivityAt = 1_000, now = 1_000, quietMs = 5_000))
        assertEquals(1_500L, quietRemainingMs(lastActivityAt = 1_000, now = 4_500, quietMs = 5_000))
        assertEquals(0L, quietRemainingMs(lastActivityAt = 1_000, now = 6_000, quietMs = 5_000))
        assertEquals(0L, quietRemainingMs(lastActivityAt = 1_000, now = 9_000, quietMs = 5_000))
    }

    @Test
    fun `입력이 이어지는 동안에는 준비하지 않고 입력이 멎으면 준비한다`() {
        val done = CompletableDeferred<Long>()
        val builder: ContextBuilder = mockk { coEvery { build("s1") } returns AppResult.Success(context(history.take(2))) }
        val runner: ModelRunner = mockk(relaxed = true) {
            coEvery { prepareConversation(any()) } coAnswers { done.complete(System.currentTimeMillis()); Unit }
        }
        val prewarmer = ConversationPrewarmer(builder, runner, defaultSessionStore) { agent(runner) }.apply { quietMs = 1_000 }

        prewarmer.afterTurn("s1")
        // 조용한 시간(1초)보다 훨씬 짧은 간격(100ms)으로 1.2초 동안 친다 — 간격을 넉넉히 둬 GC 멈춤에도 흔들리지 않게.
        val typingEnd = System.currentTimeMillis() + 1_200
        while (System.currentTimeMillis() < typingEnd) {
            prewarmer.onTyping()
            Thread.sleep(100)
        }
        val lastTyping = System.currentTimeMillis()
        assertTrue("입력 중에는 준비가 시작되지 않아야 한다", !done.isCompleted)

        val preparedAt = runBlocking { withTimeout(5_000) { done.await() } }
        assertTrue("마지막 입력 뒤 조용한 시간이 지나고 준비해야 한다", preparedAt - lastTyping >= 900)
    }
}
