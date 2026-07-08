package com.localfriday.app.assistant.agent

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.model.CalendarDraft
import com.localfriday.app.domain.model.ModelOutput
import com.localfriday.app.domain.tool.CalendarTool
import javax.inject.Inject

class CalendarAgent @Inject constructor(
    private val calendarTool: CalendarTool
) {
    suspend fun executeCalendarInsert(action: ModelOutput.CalendarDraftOutput): AppResult<Long> {
        val draft = CalendarDraft(
            title = action.title,
            startIso = action.startIso,
            endIso = action.endIso,
            note = action.note
        )
        return calendarTool.insert(draft)
    }
}
