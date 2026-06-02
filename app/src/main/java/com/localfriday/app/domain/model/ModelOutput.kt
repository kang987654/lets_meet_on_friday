package com.localfriday.app.domain.model

sealed class ModelOutput {
    // action: "text" (v0)
    data class TextOutput(
        val content: String
    ) : ModelOutput()

    // action: "calendar_draft" (v0)
    data class CalendarDraftOutput(
        val title: String,
        val startIso: String,
        val endIso: String,
        val note: String? = null,
        val confidence: Float
    ) : ModelOutput()

    // action: "search" (v1)
    data class SearchOutput(
        val query: String,
        val reason: String
    ) : ModelOutput()

    // action: "knowledge_save" (v1)
    data class KnowledgeSaveOutput(
        val content: String,
        val tags: List<String>
    ) : ModelOutput()
}
