package com.kosmos.app.domain.model

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: Role,
    val content: String,
    val inputType: InputType,
    val searchUsed: Boolean = false,
    val createdAt: Long,
    val thinkingProcess: String? = null
) {
    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM
    }
}
