package com.localfriday.app.assistant.orchestrator

data class ChatRequest(
    val sessionId: String,
    val message: String,
    val imageBytes: ByteArray? = null,
    val documentText: String? = null,
    val audioFilePath: String? = null,
    val imageTokenBudget: Int = 280,
    val onToken: ((String) -> Unit)? = null
)
