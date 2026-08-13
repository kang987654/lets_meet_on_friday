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
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.model.CalendarDraft
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.tool.CalendarTool
import com.kosmos.app.domain.util.IsoDateTimeParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidCalendarTool @Inject constructor(
    @param:ApplicationContext private val context: Context
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
            //
            // [WHY] 시작 시각 파싱 실패를 예전처럼 '지금'으로 무음 대체하지 않는다. 이전 구현은
            // 오프셋 없는 로컬 일시(`2026-08-07T15:00:00` — 툴 선언이 모델에게 지시하는 바로 그
            // 형식)를 파싱하지 못해 **정상 일정까지 전부 현재 시각으로** 기기 캘린더에 들어갔다.
            // 틀린 시각이 사용자 실캘린더에 기록되는 것은 "동기화 안 됨"보다 나쁜 무음 데이터
            // 오염이고, 호출자(AddScheduleUseCase.syncToDeviceCalendar)는 실패를 경고로 남기고
            // 로컬 저장을 유지한다(ADR-004) — 실패 반환이 곧 그 계약이다.
            val startMs = IsoDateTimeParser.toEpochMillis(draft.startIso)
                ?: return@withContext AppResult.Failure(
                    AppError.CalendarWriteError("시작 시각이 ISO 8601 형식이 아니라 기기 캘린더 동기화를 건너뜁니다")
                )

            val endIso = draft.endIso
            // [WHY] 종료 시각의 폴백은 툴 선언 문구("모르면 시작 1시간 뒤")와 같은 규칙이다.
            // 파싱 실패도 같은 폴백을 쓰되 경고를 남긴다 — 툴 경로는 실행 전 인자 검증이
            // 거르므로 여기 도달하는 실패는 다른 호출자나 회귀의 신호다.
            val endMs = if (endIso.isNullOrBlank()) {
                startMs + 3600000L // 1 hour fallback
            } else {
                IsoDateTimeParser.toEpochMillis(endIso) ?: run {
                    AppLogger.w(TAG, "endIso 파싱 실패, 시작 1시간 뒤로 폴백")
                    startMs + 3600000L
                }
            }

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

    // [WHY] Instant.toString()은 UTC 고정이라 KST 19:00 이벤트가 10:00으로 표시된다.
    // 기기 시간대의 오프셋을 포함한 ISO 문자열로 변환한다.
    private fun msToIso(ms: Long): String {
        return Instant.ofEpochMilli(ms)
            .atZone(java.time.ZoneId.systemDefault())
            .toOffsetDateTime()
            .toString()
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

    private companion object {
        const val TAG = "AndroidCalendarTool"
    }
}
