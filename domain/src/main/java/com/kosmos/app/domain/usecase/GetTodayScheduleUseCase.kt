package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.modelrunner.ModelRunner
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * [GetTodayScheduleUseCase]
 * 지정된 기간(오늘 또는 이번 주)의 일정을 앱 DB와 기기 캘린더에서 조회해 병합하는 유즈케이스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [TaskRepository], [CalendarTool]
 *
 * ### Key Flow
 * 1. 시스템 현재 시각 기준 범위(오늘/주간) 밀리초 타임스탬프 계산.
 * 2. [TaskRepository]에서 작업 목록 조회 후 해당 범위 내 일정을 [CalendarEvent]로 필터링.
 * 3. 기기 캘린더 이벤트를 병합하고, 읽기 실패는 `deviceCalendarFailed` 로 올린다 (ADR-004).
 *
 * [WHY] **요약은 여기서 만들지 않는다.** 예전에는 이 유스케이스가 `ModelRunner` 를 들고 요약
 * 추론까지 했는데, 그 때문에 (a) 캘린더 화면이 추론이 끝날 때까지 목록조차 못 보여 줬고,
 * (b) `GetScheduleToolExecutor` 를 통해 **툴 실행 도중에 추론이 한 번 더** 돌았다. 조회는 수십
 * 밀리초, 요약은 10초짜리 작업이라 한 반환값에 묶을 이유가 없다 → [SummarizeScheduleUseCase].
 */
class GetTodayScheduleUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val calendarTool: com.kosmos.app.domain.tool.CalendarTool
) {
    suspend operator fun invoke(range: ScheduleData.RangeType): AppResult<ScheduleData> = withContext(Dispatchers.IO) {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val startMs = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        
        val endMs = when (range) {
            ScheduleData.RangeType.TODAY ->
                today.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()
            // [WHY] "이번 주"는 오늘 포함 7일이므로 plusDays(6) 종료 시각까지다 (7은 8일이 됨).
            ScheduleData.RangeType.WEEK ->
                today.plusDays(6).atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()
        }

        // 1. 내부 DB 일정 조회 및 필터링
        val tasksResult = taskRepository.getPendingTasksData(0, 1000)
        if (tasksResult is AppResult.Failure) {
            return@withContext AppResult.Failure(tasksResult.error)
        }
        val tasks = (tasksResult as AppResult.Success).data
        val events = tasks.mapNotNull { task ->
            val dueIso = task.dueDateIso ?: return@mapNotNull null
            val ms = parseIsoToMs(dueIso, zoneId) ?: return@mapNotNull null
            if (ms in startMs..endMs) {
                CalendarEvent(
                    id = task.id,
                    title = task.title,
                    startIso = dueIso,
                    endIso = task.endDateIso ?: dueIso,
                    location = null,
                    description = task.description
                ) to ms
            } else {
                null
            }
        }

        // 2. 기기 시스템 캘린더 이벤트 병합 (권한 미보유 등 실패 시 로컬 일정만 사용 — ADR-004)
        //
        // [WHY] 실패를 **삼키지 않고 플래그로 올린다.** 예전에는 `emptyList()` 로 조용히 넘겨
        // 화면에 앱 일정만 나왔고, 사용자는 기기 일정이 없다고 믿었다 — 틀린 답을 성공처럼
        // 보여 주는 결함이다 (PRD EC4).
        var deviceCalendarFailed = false
        val deviceEvents = when (val deviceResult = calendarTool.readEvents(startMs, endMs)) {
            is AppResult.Success -> deviceResult.data.mapNotNull { event ->
                val ms = parseIsoToMs(event.startIso, zoneId) ?: return@mapNotNull null
                event to ms
            }
            is AppResult.Failure -> {
                deviceCalendarFailed = true
                emptyList()
            }
        }

        // [WHY] AddSchedule로 로컬 저장 + 기기 동기화된 일정이 두 번 표시되지 않도록
        // (제목, 시작 시각) 기준으로 기기 이벤트를 중복 제거한다.
        val localKeys = events.map { it.first.title to it.second }.toSet()
        val mergedEvents = events + deviceEvents.filter { (event, ms) -> (event.title to ms) !in localKeys }

        // [WHY] ISO 문자열 사전순 정렬은 오프셋/로컬 포맷이 섞이면 시간 순서를 보장하지 못하므로
        // 파싱된 epoch ms 기준으로 정렬한다.
        val sortedEvents = mergedEvents.sortedBy { it.second }.map { it.first }

        // [WHY] 요약은 이 유스케이스가 만들지 않는다 — `SummarizeScheduleUseCase` 로 분리했다.
        // 예전에는 여기서 요약 추론(~10초)을 **기다린 뒤** 결과를 냈고, 그동안 캘린더 화면은
        // 스피너만 돌았다. 조회와 요약은 수명이 다른 작업이라 한 반환값에 묶으면 느린 쪽이
        // 빠른 쪽을 인질로 잡는다.
        AppResult.Success(
            ScheduleData(
                events = sortedEvents.toImmutableList(),
                summary = null,
                rangeType = range,
                deviceCalendarFailed = deviceCalendarFailed
            )
        )
    }

    /**
     * ISO-8601 계열 문자열을 epoch ms로 변환합니다.
     * [WHY] 오프셋 포함(`+09:00`, `-05:00`), UTC(`Z`), 로컬 일시, 날짜-only 네 가지 포맷을
     * 표준 파서 폴백 체인으로 처리한다. 파싱 규칙은 화면(CalendarViewModel 날짜 필터)과도
     * 공유해야 하므로 [IsoDateTimeParser]에 위임한다 — 규칙이 갈라지면 목록과 필터가 어긋난다.
     */
    private fun parseIsoToMs(iso: String, zoneId: ZoneId): Long? =
        com.kosmos.app.domain.util.IsoDateTimeParser.toEpochMillis(iso, zoneId)
}
