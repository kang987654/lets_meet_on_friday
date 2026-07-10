package com.kosmos.app.domain.modelrunner

import com.kosmos.app.domain.model.ChatMessage

data class ChatPrompt(
    val sessionId: String,
    val systemInstruction: String,
    val history: List<ChatMessage>,
    val currentInput: String
)
