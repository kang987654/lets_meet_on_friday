package com.kosmos.app.domain.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.CalendarDraft
import com.kosmos.app.domain.model.CalendarEvent

/**
 * [v0] 캘린더 연동 도구
 */
interface CalendarTool {
    suspend fun readEvents(startMs: Long, endMs: Long): AppResult<List<CalendarEvent>>
    suspend fun insert(draft: CalendarDraft): AppResult<Long>
}
