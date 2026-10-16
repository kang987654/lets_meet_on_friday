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
    /** 이 답변이 SearchMemory 로 참조한 에피소드 id 목록 — 회수 칩의 데이터. */
    val recallEpisodeIds: List<String> = emptyList(),
    /** 이 답변이 만든·근거로 쓴 대상 — 결과 카드의 데이터(0.38.2). null = 결과 없음(글만). */
    val result: MessageResult? = null
) {
    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM
    }
}
