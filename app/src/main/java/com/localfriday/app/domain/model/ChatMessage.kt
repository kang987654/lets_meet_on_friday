package com.localfriday.app.domain.model

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: Role,
    val content: String,
    val inputType: InputType,
    val searchUsed: Boolean = false,
    val createdAt: Long
) {
    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM
    }
}
