package com.localfriday.app.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class ScheduleData(
    val events: ImmutableList<CalendarEvent>,
    val summary: String?,
    val rangeType: RangeType
) {
    enum class RangeType {
        TODAY,
        WEEK
    }
}
