package com.localfriday.app.domain.modelrunner

import com.localfriday.app.domain.model.ChatMessage

data class ChatPrompt(
    val sessionId: String,
    val systemInstruction: String,
    val history: List<ChatMessage>,
    val currentInput: String
)
