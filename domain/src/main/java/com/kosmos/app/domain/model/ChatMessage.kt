package com.kosmos.app.domain.model

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: Role,
    val content: String,
    val inputType: InputType,
    val searchUsed: Boolean = false,
    val createdAt: Long,
    val thinkingProcess: String? = null,
    /** 이 메시지가 속한 에피소드 (ADR-022). null = 미배정. */
    val episodeId: String? = null,
    /** 이 답변이 SearchMemory 로 참조한 에피소드 id 목록 — 회수 칩(🧠)의 데이터. */
    val recallEpisodeIds: List<String> = emptyList()
) {
    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM
    }
}
