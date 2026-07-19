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
import javax.inject.Inject

class GetTodayScheduleUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val modelRunner: ModelRunner
) {
    suspend operator fun invoke(range: ScheduleData.RangeType): AppResult<ScheduleData> = withContext(Dispatchers.IO) {
        val today = LocalDate.now(ZoneId.systemDefault())
        val startMs = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        val endMs = when (range) {
            ScheduleData.RangeType.TODAY -> 
                today.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            ScheduleData.RangeType.WEEK -> 
                today.plusDays(7).atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        // 1. 내부 DB 일정 조회 및 필터링
        val tasksResult = taskRepository.getPendingTasksData(0, 1000)
        if (tasksResult is AppResult.Failure) {
            return@withContext AppResult.Failure(tasksResult.error)
        }
        val tasks = (tasksResult as AppResult.Success).data
        val events = tasks.mapNotNull { task ->
            val dueIso = task.dueDateIso ?: return@mapNotNull null
            val ms = parseIsoToMs(dueIso) ?: return@mapNotNull null
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
            is AppResult.Failure -> null // AI 요약 실패 시 null 반환 (Fallback)
        }

        AppResult.Success(
            ScheduleData(
                events = events.toImmutableList(),
                summary = summary,
                rangeType = range
            )
        )
    }

    private fun parseIsoToMs(iso: String): Long? {
        return try {
            if (iso.contains("Z") || iso.contains("+") || (iso.count { it == '-' } > 1 && iso.indexOf("+") > 0)) {
                java.time.Instant.parse(iso).toEpochMilli()
            } else {
                java.time.LocalDateTime.parse(iso)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        } catch (e: Exception) {
            try {
                java.time.LocalDate.parse(iso)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (e2: Exception) {
                null
            }
        }
    }
}
