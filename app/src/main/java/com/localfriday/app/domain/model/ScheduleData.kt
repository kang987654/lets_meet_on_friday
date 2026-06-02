package com.localfriday.app.domain.model

data class ScheduleData(
    val events: List<CalendarEvent>,
    val summary: String?,
    val rangeType: RangeType
) {
    enum class RangeType {
        TODAY,
        WEEK
    }
}
