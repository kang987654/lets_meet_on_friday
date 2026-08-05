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
 * 지정된 기간(오늘 또는 이번 주)의 일정을 DB에서 조회하고, Gemma LLM을 통해 간결한 1~2문장의 요약(Summary)을 생성하는 유즈케이스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [TaskRepository], [ModelRunner]
 *
 * ### Key Flow
 * 1. 시스템 현재 시각 기준 범위(오늘/주간) 밀리초 타임스탬프 계산.
 * 2. [TaskRepository]에서 작업 목록 조회 후 해당 범위 내 일정을 [CalendarEvent]로 필터링.
 * 3. 일정이 존재할 경우 [ModelRunner]에 요약 프롬프트를 전달하여 친절한 간결 요약문 생성 (실패 시 null 폴백).
 */
class GetTodayScheduleUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val modelRunner: ModelRunner,
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
        val deviceEvents = when (val deviceResult = calendarTool.readEvents(startMs, endMs)) {
            is AppResult.Success -> deviceResult.data.mapNotNull { event ->
                val ms = parseIsoToMs(event.startIso, zoneId) ?: return@mapNotNull null
                event to ms
            }
            is AppResult.Failure -> emptyList()
        }

        // [WHY] AddSchedule로 로컬 저장 + 기기 동기화된 일정이 두 번 표시되지 않도록
        // (제목, 시작 시각) 기준으로 기기 이벤트를 중복 제거한다.
        val localKeys = events.map { it.first.title to it.second }.toSet()
        val mergedEvents = events + deviceEvents.filter { (event, ms) -> (event.title to ms) !in localKeys }

        // [WHY] ISO 문자열 사전순 정렬은 오프셋/로컬 포맷이 섞이면 시간 순서를 보장하지 못하므로
        // 파싱된 epoch ms 기준으로 정렬한다.
        val sortedEvents = mergedEvents.sortedBy { it.second }.map { it.first }

        if (sortedEvents.isEmpty()) {
            return@withContext AppResult.Success(
                ScheduleData(events = persistentListOf(), summary = null, rangeType = range)
            )
        }

        // 3. AI 요약 (프롬프트 구성)
        val eventListText = sortedEvents.joinToString("\n") {
            "- ${it.title} (시간: ${it.startIso})"
        }
        
        val rangeStr = if (range == ScheduleData.RangeType.TODAY) "오늘" else "이번 주"
        val prompt = """
            다음은 사용자의 $rangeStr 일정입니다. 친절한 비서처럼 간결하게 1~2문장으로 요약해주세요.
            
            일정:
            $eventListText
        """.trimIndent()

        val chatPrompt = com.kosmos.app.domain.modelrunner.ChatPrompt(
            sessionId = "schedule-summary",
            systemInstruction = "[System]\nYou are an assistant summarizing today's schedule.",
            history = emptyList(),
            currentInput = prompt
        )
        val summary = when (val result = modelRunner.generate(chatPrompt)) {
            is AppResult.Success -> result.data.trim()
            is AppResult.Failure -> null // [WHY] AI 요약 실패 시 앱 다운을 막기 위해 null 폴백 처리
        }

        AppResult.Success(
            ScheduleData(
                events = sortedEvents.toImmutableList(),
                summary = summary,
                rangeType = range
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
