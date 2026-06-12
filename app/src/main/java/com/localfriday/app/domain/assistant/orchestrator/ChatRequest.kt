package com.localfriday.app.domain.assistant.orchestrator

data class ChatRequest(
    val sessionId: String,
    val message: String,
    val imageBytes: ByteArray? = null,
    val documentText: String? = null,
    val onToken: ((String) -> Unit)? = null
)
