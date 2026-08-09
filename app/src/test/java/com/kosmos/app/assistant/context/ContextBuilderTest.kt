package com.kosmos.app.assistant.context

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.tool.Tokenizer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextBuilderTest {

    private lateinit var contextBuilder: ContextBuilder
    private val conversationRepository: ConversationRepository = mockk()
    private val tokenizer: Tokenizer = mockk()
    private val settingsDataStore: SettingsDataStore = mockk()

    @Before
    fun setup() {
        contextBuilder = ContextBuilder(conversationRepository, tokenizer, settingsDataStore)
    }

    @Test
    fun `build returns Context with mapped responseStyle from settingsDataStore`() = runTest {
        val testSessionId = "session-123"
        val testStyle = "친절하게 이모지 많이 써줘"
        
        every { settingsDataStore.responseStyleFlow } returns flowOf(testStyle)
        coEvery { conversationRepository.getRecentBySession(testSessionId, any()) } returns AppResult.Success(emptyList())
        every { tokenizer.sizeInTokens(any<String>()) } returns 10
        
        val result = contextBuilder.build(testSessionId)
        
        assertTrue(result is AppResult.Success)
        val context = (result as AppResult.Success).data
        assertEquals(testSessionId, context.sessionId)
        assertEquals(testStyle, context.responseStyle)
    }

    // --- 프리필 예산 회계 ---

    /** 지정한 토큰 수를 갖는 메시지 [count] 개와, 그것을 반환하는 스텁을 준비한다. */
    private fun stubMessages(count: Int, tokensEach: Int, maxTokens: Int) {
        val messages = (1..count).map { index ->
            ChatMessage(
                id = "m$index",
                sessionId = "s1",
                role = ChatMessage.Role.USER,
                content = "msg$index",
                inputType = com.kosmos.app.domain.model.InputType.TEXT,
                createdAt = index.toLong()
            )
        }
        every { settingsDataStore.responseStyleFlow } returns flowOf("DEFAULT")
        every { settingsDataStore.maxTokensFlow } returns flowOf(maxTokens)
        every { settingsDataStore.webSearchEnabledFlow } returns flowOf(false)
        coEvery { conversationRepository.getRecentBySession(any(), any()) } returns AppResult.Success(messages)
        every { tokenizer.sizeInTokens(any<String>()) } returns tokensEach
    }

    @Test
    fun `히스토리 예산에서 시스템 지시·툴 선언 오버헤드가 예약된다`() = runTest {
        // [WHY] 이전에는 설정값 전체를 히스토리에 썼고 시스템 지시·툴 선언(~2천 토큰)·few-shot 을
        // 세지 않아, "컨텍스트 윈도우 4096" 설정에도 실제 프리필이 그것을 크게 넘었다.
        // 예산 6000 - 오버헤드 2600 = 3400 이므로 1010 토큰짜리 메시지는 3개만 들어가야 한다.
        stubMessages(count = 10, tokensEach = 1000, maxTokens = 6000)

        val result = contextBuilder.build("s1")

        val context = (result as AppResult.Success).data
        assertEquals(3, context.recentConversations.size)
    }

    @Test
    fun `설정을 최소로 내려도 최소 히스토리 예산은 보장된다`() = runTest {
        // [WHY] 오버헤드가 설정값보다 크면 예산이 음수가 되어 히스토리가 통째로 사라진다 —
        // 직전 대화를 못 보면 대화가 성립하지 않으므로 하한을 둔다(500 / 210 = 2개).
        stubMessages(count = 10, tokensEach = 200, maxTokens = 1000)

        val result = contextBuilder.build("s1")

        val context = (result as AppResult.Success).data
        assertEquals(2, context.recentConversations.size)
    }

    @Test
    fun `가장 최근 메시지부터 담고 시간순으로 되돌린다`() = runTest {
        stubMessages(count = 5, tokensEach = 1000, maxTokens = 6000)

        val result = contextBuilder.build("s1")

        val context = (result as AppResult.Success).data
        // 최신 3개(m3, m4, m5)가 시간순으로 유지되어야 한다.
        assertEquals(listOf("m3", "m4", "m5"), context.recentConversations.map { it.id })
    }

    @Test
    fun `대화 조회는 화면과 같은 후보 수를 요청한다`() = runTest {
        // [WHY] 이전에는 150 이 하드코딩돼 있었고 화면은 계약 기본값(5)을 썼다 — 같은 데이터를
        // 두 곳이 다른 수로 읽었다. 상수 하나로 묶었는지 고정한다.
        stubMessages(count = 1, tokensEach = 10, maxTokens = 6000)

        contextBuilder.build("s1")

        io.mockk.coVerify {
            conversationRepository.getRecentBySession(
                "s1",
                com.kosmos.app.core.common.Constants.MAX_RECENT_CONVERSATIONS
            )
        }
    }

    @Test
    fun `build returns Context with DEFAULT responseStyle when flow throws exception`() = runTest {
        val testSessionId = "session-123"
        
        every { settingsDataStore.responseStyleFlow } returns kotlinx.coroutines.flow.flow { throw RuntimeException("Error") }
        coEvery { conversationRepository.getRecentBySession(testSessionId, any()) } returns AppResult.Success(emptyList())
        every { tokenizer.sizeInTokens(any<String>()) } returns 10
        
        val result = contextBuilder.build(testSessionId)
        
        assertTrue(result is AppResult.Success)
        val context = (result as AppResult.Success).data
        assertEquals("DEFAULT", context.responseStyle)
    }
}
