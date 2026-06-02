package com.localfriday.app.domain.model

data class AssistantResponse(
    val id: String,
    val sessionId: String,
    val content: String,
    val actionCards: List<ActionCard> = emptyList(),
    val createdAt: Long
)
