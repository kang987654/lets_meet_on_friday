package com.localfriday.app.domain.tool

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.model.CalendarDraft
import com.localfriday.app.domain.model.CalendarEvent

/**
 * [v0] 캘린더 연동 도구
 */
interface CalendarTool {
    suspend fun readEvents(startMs: Long, endMs: Long): AppResult<List<CalendarEvent>>
    suspend fun insert(draft: CalendarDraft): AppResult<Long>
}
