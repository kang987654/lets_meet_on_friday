package com.kosmos.app.platform.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.CalendarDraft
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.tool.CalendarTool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidCalendarTool @Inject constructor(
    @ApplicationContext private val context: Context
) : CalendarTool {

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    override suspend fun readEvents(startMs: Long, endMs: Long): AppResult<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            return@withContext AppResult.Failure(AppError.PermissionDenied(Manifest.permission.READ_CALENDAR))
        }

        try {
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DESCRIPTION
            )
            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTEND} <= ?"
            val selectionArgs = arrayOf(startMs.toString(), endMs.toString())

            val cursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )

            val events = mutableListOf<CalendarEvent>()
            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleIdx = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val startIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val locationIdx = it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                val descIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)

                while (it.moveToNext()) {
                    events.add(
                        CalendarEvent(
                            id = it.getLong(idIdx).toString(),
                            title = it.getString(titleIdx) ?: "",
                            startIso = msToIso(it.getLong(startIdx)),
                            endIso = msToIso(it.getLong(endIdx)),
                            location = it.getString(locationIdx),
                            description = it.getString(descIdx)
                        )
                    )
                }
            }
            AppResult.Success(events)
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.PermissionDenied(Manifest.permission.READ_CALENDAR))
        } catch (e: Exception) {
            AppResult.Failure(AppError.CalendarReadError(e.message ?: "알 수 없는 에러"))
        }
    }

    override suspend fun insert(draft: CalendarDraft): AppResult<Long> = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.WRITE_CALENDAR)) {
            return@withContext AppResult.Failure(AppError.PermissionDenied(Manifest.permission.WRITE_CALENDAR))
        }

        try {
            // 중복/겹침 감지는 상위 레이어에서 처리하여 경고를 띄우도록 정책 설정 (하드-페일 금지)
            val startMs = isoToMs(draft.startIso)
            val endMs = isoToMs(draft.endIso)

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMs)
                put(CalendarContract.Events.DTEND, endMs)
                put(CalendarContract.Events.TITLE, draft.title)
                put(CalendarContract.Events.DESCRIPTION, draft.note)
                put(CalendarContract.Events.CALENDAR_ID, getDefaultCalendarId())
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                val eventId = uri.lastPathSegment?.toLongOrNull()
                if (eventId != null) {
                    AppResult.Success(eventId)
                } else {
                    AppResult.Failure(AppError.CalendarWriteError("이벤트 ID를 가져올 수 없습니다."))
                }
            } else {
                AppResult.Failure(AppError.CalendarWriteError("이벤트 추가 실패"))
            }
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.PermissionDenied(Manifest.permission.WRITE_CALENDAR))
        } catch (e: Exception) {
            AppResult.Failure(AppError.CalendarWriteError(e.message ?: "알 수 없는 에러"))
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun msToIso(ms: Long): String {
        return Instant.ofEpochMilli(ms).toString()
    }

    private fun isoToMs(iso: String): Long {
        return Instant.parse(iso).toEpochMilli()
    }
    
    private fun getDefaultCalendarId(): Long {
        var calId = 1L
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) return calId
        try {
            val projection = arrayOf(CalendarContract.Calendars._ID)
            val selection = "${CalendarContract.Calendars.IS_PRIMARY} = 1"
            val cursor = contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection, selection, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    calId = it.getLong(0)
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return calId
    }
}
