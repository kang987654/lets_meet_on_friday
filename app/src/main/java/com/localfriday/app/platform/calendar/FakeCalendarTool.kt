package com.localfriday.app.platform.calendar

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.model.CalendarDraft
import com.localfriday.app.domain.model.CalendarEvent
import com.localfriday.app.domain.tool.CalendarTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeCalendarTool @Inject constructor() : CalendarTool {
    private val events = mutableListOf<CalendarEvent>()
    private val idCounter = AtomicLong(1L)

    override suspend fun readEvents(startMs: Long, endMs: Long): AppResult<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        val result = events.filter {
            val eventStartMs = Instant.parse(it.startIso).toEpochMilli()
            val eventEndMs = Instant.parse(it.endIso).toEpochMilli()
            eventStartMs >= startMs && eventEndMs <= endMs
        }
        AppResult.Success(result)
    }

    override suspend fun insert(draft: CalendarDraft): AppResult<Long> = withContext(Dispatchers.IO) {
        val newId = idCounter.getAndIncrement()
        events.add(
            CalendarEvent(
                id = newId.toString(),
                title = draft.title,
                startIso = draft.startIso,
                endIso = draft.endIso,
                description = draft.note
            )
        )
        AppResult.Success(newId)
    }
}
