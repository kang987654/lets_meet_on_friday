package com.kosmos.app.domain.model

import com.kosmos.app.core.common.Constants

data class CalendarDraft(
    val title: String,
    val startIso: String,
    val endIso: String,
    val note: String? = null,
    val confidence: Float
) {
    fun isConfident(): Boolean = confidence >= Constants.CALENDAR_DRAFT_MIN_CONFIDENCE
}
