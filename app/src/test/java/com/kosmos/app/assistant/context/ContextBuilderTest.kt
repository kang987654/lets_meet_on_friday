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
