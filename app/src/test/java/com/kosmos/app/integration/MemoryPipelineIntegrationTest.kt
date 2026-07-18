package com.kosmos.app.integration

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.TextEmbedder
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.usecase.SaveKnowledgeUseCase
import com.kosmos.app.domain.usecase.SearchKnowledgeUseCase
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.data.local.prefs.SettingsDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MemoryPipelineIntegrationTest {

    @Test
    fun `test complete memory write and read flow`() = runBlocking {
        // Setup Mocks
        val repository: KnowledgeRepository = mockk()
        val embedder: TextEmbedder = mockk()
        val convRepo: ConversationRepository = mockk()
        val tokenizer: Tokenizer = mockk()
        val settingsDataStore: SettingsDataStore = mockk()

        val saveUseCase = SaveKnowledgeUseCase(repository, embedder)
        val searchUseCase = SearchKnowledgeUseCase(repository, embedder)
        
        val contextBuilder = ContextBuilder(
            conversationRepository = convRepo,
            searchKnowledgeUseCase = searchUseCase,
            tokenizer = tokenizer,
            settingsDataStore = settingsDataStore
        )

        // Mock Behavior
        val testNote = KnowledgeNote(
            id = UUID.randomUUID().toString(),
            content = "나 매운 거 못 먹어",
            tags = listOf("food"),
            embedding = floatArrayOf(0.1f, 0.2f),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        coEvery { embedder.embed(any()) } returns AppResult.Success(floatArrayOf(0.1f, 0.2f))
        coEvery { repository.save(any()) } returns AppResult.Success(Unit)
        
        // Write Memory
        val saveResult = saveUseCase("나 매운 거 못 먹어", listOf("food"))
        assertTrue(saveResult is AppResult.Success)

        // Setup Read Flow Mocks
        val testMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = "test-session",
            role = ChatMessage.Role.USER,
            content = "오늘 점심 추천해 줘",
            inputType = com.kosmos.app.domain.model.InputType.TEXT,
            searchUsed = false,
            createdAt = System.currentTimeMillis()
        )
        coEvery { convRepo.getRecentBySession("test-session", any()) } returns AppResult.Success(listOf(testMessage))
        coEvery { repository.searchByVector(any(), any()) } returns AppResult.Success(listOf(testNote))
        every { tokenizer.sizeInTokens(any()) } returns 10
        every { settingsDataStore.responseStyleFlow } returns flowOf("DEFAULT")

        // Build context and verify RAG injection
        val contextResult = contextBuilder.build("test-session")
        assertTrue(contextResult is AppResult.Success)
        
        val context = (contextResult as AppResult.Success).data
        val systemMessage = context.recentConversations.find { it.role == ChatMessage.Role.SYSTEM && it.content.contains("과거 기억") }
        
        assertTrue("System message with memory should be injected", systemMessage != null)
        assertTrue("Injected memory should contain saved fact", systemMessage!!.content.contains("나 매운 거 못 먹어"))
    }
}
