package com.kosmos.app.domain.model

data class CalendarDraft(
    val title: String,
    val startIso: String,
    val endIso: String?,
    val note: String? = null,
    val confidence: Float
)
