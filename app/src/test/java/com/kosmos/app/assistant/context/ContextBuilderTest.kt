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
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.memory.ProfileRepository
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.testing.FakeLibraryRepository

class ContextBuilderTest {

    private lateinit var contextBuilder: ContextBuilder
    private val conversationRepository: ConversationRepository = mockk()
    private val tokenizer: Tokenizer = mockk()
    private val settingsDataStore: SettingsDataStore = mockk()
    private val profileRepository: ProfileRepository = mockk {
        io.mockk.coEvery { getEntries() } returns AppResult.Success(emptyList())
    }

    @Before
    fun setup() {
        contextBuilder = ContextBuilder(conversationRepository, tokenizer, settingsDataStore, profileRepository, FakeLibraryRepository())
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
                inputType = InputType.TEXT,
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
        // [WHY] 이전에는 설정값 전체를 히스토리에 썼고 시스템 지시·툴 선언·few-shot 을 세지 않아
        // 실제 프리필이 설정값을 넘었다.
        //
        // [WHY] 값이 6000/2600 이었다. 6000 은 엔진 KV 천장(3328)을 넘어 **도달할 수 없는 설정**이
        // 됐고, 오버헤드 실측이 1,329 로 드러나 예약도 1400 으로 내려갔다. 실제로 쓰이는 범위로
        // 옮긴다 — 예산 3100 - 오버헤드 1500 = 1600 이므로 510 토큰짜리 메시지는 3개만 들어간다(예약이 오를 때마다 예산도 같이 올려
        // 남는 히스토리 1600 을 유지한다 — 0.38.6 M4 예약 1,500).
        stubMessages(count = 10, tokensEach = 500, maxTokens = 3100)

        val result = contextBuilder.build("s1")

        val context = (result as AppResult.Success).data
        assertEquals(3, context.recentConversations.size)
    }

    @Test
    fun `설정을 최소로 내려도 최소 히스토리 예산은 보장된다`() = runTest {
        // [WHY] 오버헤드가 설정값보다 크면 예산이 음수가 되어 히스토리가 통째로 사라진다 —
        // 직전 대화를 못 보면 대화가 성립하지 않으므로 하한을 둔다(300 / 110 = 2개).
        // 하한은 500 이었으나 예산이 GPU 발병점 아래(1700)로 내려오며 300 이 됐다 (ADR-021).
        stubMessages(count = 10, tokensEach = 100, maxTokens = 1000)

        val result = contextBuilder.build("s1")

        val context = (result as AppResult.Success).data
        assertEquals(2, context.recentConversations.size)
    }

    @Test
    fun `가장 최근 메시지부터 담고 시간순으로 되돌린다`() = runTest {
        stubMessages(count = 5, tokensEach = 500, maxTokens = 3100)

        val result = contextBuilder.build("s1")

        val context = (result as AppResult.Success).data
        // 최신 3개(m3, m4, m5)가 시간순으로 유지되어야 한다.
        assertEquals(listOf("m3", "m4", "m5"), context.recentConversations.map { it.id })
    }

    @Test
    fun `대화 조회는 화면과 같은 후보 수를 요청한다`() = runTest {
        // [WHY] 이전에는 150 이 하드코딩돼 있었고 화면은 계약 기본값(5)을 썼다 — 같은 데이터를
        // 두 곳이 다른 수로 읽었다. 상수 하나로 묶었는지 고정한다.
        stubMessages(count = 1, tokensEach = 10, maxTokens = 3000)

        contextBuilder.build("s1")

        io.mockk.coVerify {
            conversationRepository.getRecentBySession(
                "s1",
                Constants.MAX_RECENT_CONVERSATIONS
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

    // --- 오래 쉰 뒤 지난 대화 (0.41.7 M4) ---

    private val minute = 60_000L

    private fun msg(id: String, role: ChatMessage.Role, at: Long, episode: String) =
        ChatMessage(id = id, sessionId = "s1", role = role, content = id, inputType = InputType.TEXT, createdAt = at, episodeId = episode)

    private fun episode(id: String, status: com.kosmos.app.domain.model.EpisodeStatus, summary: String?) = com.kosmos.app.domain.model.Episode(
        id = id, sessionId = "s1", status = status, title = "노트북 비밀번호 문의", summary = summary,
        tags = emptyList(), startAt = 0, endAt = 0, messageCount = 2, retryCount = 0, createdAt = 0, updatedAt = 0
    )

    private fun gapBuilder(epStatus: com.kosmos.app.domain.model.EpisodeStatus, summary: String?): ContextBuilder {
        every { settingsDataStore.responseStyleFlow } returns flowOf("DEFAULT")
        every { settingsDataStore.maxTokensFlow } returns flowOf(Constants.DEFAULT_CONTEXT_TOKENS)
        every { settingsDataStore.webSearchEnabledFlow } returns flowOf(false)
        every { tokenizer.sizeInTokens(any<String>()) } returns 5
        val episodes: com.kosmos.app.domain.memory.EpisodeRepository = mockk {
            coEvery { getById("old") } returns AppResult.Success(episode("old", epStatus, summary))
        }
        return ContextBuilder(conversationRepository, tokenizer, settingsDataStore, profileRepository, FakeLibraryRepository(), episodeRepository = episodes)
    }

    @Test
    fun `30분 넘게 쉬었다 돌아오면 지난 대화는 요약 한 줄로 바뀌고 원문은 빠진다`() = runTest {
        val builder = gapBuilder(com.kosmos.app.domain.model.EpisodeStatus.SUMMARIZED, "사용자가 노트북 비밀번호를 물었어요.")
        val now = 200 * minute
        coEvery { conversationRepository.getRecentBySession(any(), any()) } returns AppResult.Success(
            listOf(
                msg("q", ChatMessage.Role.USER, 10 * minute, "old"),
                msg("a", ChatMessage.Role.ASSISTANT, 11 * minute, "old"),
                msg("아아 테스트", ChatMessage.Role.USER, now, "new")
            )
        )

        val context = (builder.build("s1", now) as AppResult.Success).data

        assertEquals(listOf("아아 테스트"), context.recentConversations.map { it.id })
        assertEquals("(과거 대화) 노트북 비밀번호 문의: 사용자가 노트북 비밀번호를 물었어요.", context.pastEpisodeLine)
    }

    @Test
    fun `돌아와서 아직 말하기 전(미리 준비)에도 같은 요약 줄과 빈 기록이 된다`() = runTest {
        val builder = gapBuilder(com.kosmos.app.domain.model.EpisodeStatus.SUMMARIZED, "요약")
        coEvery { conversationRepository.getRecentBySession(any(), any()) } returns AppResult.Success(
            listOf(msg("q", ChatMessage.Role.USER, 10 * minute, "old"), msg("a", ChatMessage.Role.ASSISTANT, 11 * minute, "old"))
        )

        val context = (builder.build("s1", now = 200 * minute) as AppResult.Success).data

        assertTrue(context.recentConversations.isEmpty())
        assertTrue(context.pastEpisodeLine.isNotEmpty())
    }

    @Test
    fun `지난 대화 요약이 아직 없으면 지금처럼 원문을 싣는다`() = runTest {
        val builder = gapBuilder(com.kosmos.app.domain.model.EpisodeStatus.CLOSED, null)
        val now = 200 * minute
        coEvery { conversationRepository.getRecentBySession(any(), any()) } returns AppResult.Success(
            listOf(msg("q", ChatMessage.Role.USER, 10 * minute, "old"), msg("새 말", ChatMessage.Role.USER, now, "new"))
        )

        val context = (builder.build("s1", now) as AppResult.Success).data

        assertEquals(listOf("q", "새 말"), context.recentConversations.map { it.id })
        assertEquals("", context.pastEpisodeLine)
    }

    @Test
    fun `쉬지 않고 이어진 대화는 그대로다`() = runTest {
        val builder = gapBuilder(com.kosmos.app.domain.model.EpisodeStatus.SUMMARIZED, "요약")
        val now = 30 * minute
        coEvery { conversationRepository.getRecentBySession(any(), any()) } returns AppResult.Success(
            listOf(msg("q", ChatMessage.Role.USER, 10 * minute, "old"), msg("a", ChatMessage.Role.ASSISTANT, 11 * minute, "old"), msg("다음", ChatMessage.Role.USER, now, "old"))
        )

        val context = (builder.build("s1", now) as AppResult.Success).data

        assertEquals(listOf("q", "a", "다음"), context.recentConversations.map { it.id })
        assertEquals("", context.pastEpisodeLine)
    }
}
