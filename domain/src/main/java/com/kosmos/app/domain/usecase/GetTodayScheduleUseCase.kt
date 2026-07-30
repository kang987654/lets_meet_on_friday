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
    private val modelRunner: ModelRunner
) {
    suspend operator fun invoke(range: ScheduleData.RangeType): AppResult<ScheduleData> = withContext(Dispatchers.IO) {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val startMs = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        
        val endMs = when (range) {
            ScheduleData.RangeType.TODAY -> 
                today.atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()
            ScheduleData.RangeType.WEEK -> 
                today.plusDays(7).atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()
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
                    endIso = dueIso,
                    location = null,
                    description = null
                )
            } else {
                null
            }
        }.sortedBy { it.startIso }

        if (events.isEmpty()) {
            return@withContext AppResult.Success(
                ScheduleData(events = persistentListOf(), summary = null, rangeType = range)
            )
        }

        // 2. AI 요약 (프롬프트 구성)
        val eventListText = events.joinToString("\n") { 
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
                events = events.toImmutableList(),
                summary = summary,
                rangeType = range
            )
        )
    }

    private fun parseIsoToMs(iso: String, zoneId: ZoneId): Long? {
        return runCatching {
            if (iso.contains("Z") || iso.contains("+") || (iso.count { it == '-' } > 1 && iso.indexOf("+") > 0)) {
                Instant.parse(iso).toEpochMilli()
            } else {
                LocalDateTime.parse(iso).atZone(zoneId).toInstant().toEpochMilli()
            }
        }.getOrNull() ?: runCatching {
            LocalDate.parse(iso).atStartOfDay(zoneId).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
