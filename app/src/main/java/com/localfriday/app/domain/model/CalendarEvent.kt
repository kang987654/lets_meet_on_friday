package com.localfriday.app.domain.model

data class CalendarEvent(
    val id: String,
    val title: String,
    val startIso: String,
    val endIso: String,
    val location: String? = null,
    val description: String? = null
)
