package com.kosmos.app.domain.model

sealed class ActionPayload {
    data class CalendarDraftPayload(val draft: CalendarDraft) : ActionPayload()
    data class KnowledgeSavePayload(val note: KnowledgeNote) : ActionPayload()
}

data class ActionCard(
    val actionType: ActionType,
    val payload: ActionPayload
) {
    enum class ActionType {
        CALENDAR_DRAFT,
        KNOWLEDGE_SAVE
    }
}
