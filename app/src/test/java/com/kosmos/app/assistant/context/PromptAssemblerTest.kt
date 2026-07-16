package com.kosmos.app.assistant.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PromptAssemblerTest {

    private lateinit var promptAssembler: PromptAssembler

    @Before
    fun setup() {
        promptAssembler = PromptAssembler()
    }

    @Test
    fun `assemble system block correctly without custom response style when DEFAULT`() {
        val testContext = ContextBuilder.Context(
            recentConversations = emptyList(),
            sessionId = "session-1",
            responseStyle = "DEFAULT"
        )
        
        val chatPrompt = promptAssembler.assemble(testContext, "test input")
        
        assertTrue(chatPrompt.systemInstruction.contains("[System]"))
        assertTrue(chatPrompt.systemInstruction.contains("You are a helpful personal assistant named Local Friday."))
        assertFalse(chatPrompt.systemInstruction.contains("User's preferred response style:"))
    }

    @Test
    fun `assemble system block correctly with custom response style when not DEFAULT`() {
        val testStyle = "친절하게 이모지 많이 써줘"
        val testContext = ContextBuilder.Context(
            recentConversations = emptyList(),
            sessionId = "session-1",
            responseStyle = testStyle
        )
        
        val chatPrompt = promptAssembler.assemble(testContext, "test input")
        
        assertTrue(chatPrompt.systemInstruction.contains("[System]"))
        assertTrue(chatPrompt.systemInstruction.contains("You are a helpful personal assistant named Local Friday."))
        assertTrue(chatPrompt.systemInstruction.contains("User's preferred response style: $testStyle. You MUST strictly follow this style when answering."))
    }
}
