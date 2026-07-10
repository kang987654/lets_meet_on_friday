package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.tool.CalendarTool
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
    private val calendarTool: CalendarTool,
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

        // 1. 일정 조회
        val eventsResult = calendarTool.readEvents(startMs, endMs)
        if (eventsResult is AppResult.Failure) {
            return@withContext AppResult.Failure(eventsResult.error)
        }
        val events = (eventsResult as AppResult.Success).data

        if (events.isEmpty()) {
            return@withContext AppResult.Success(
                ScheduleData(events = persistentListOf(), summary = null, rangeType = range)
            )
        }

        // 2. AI 요약 (프롬프트 구성)
        val eventListText = events.joinToString("\n") { 
            "- ${it.title} (시작: ${it.startIso}, 종료: ${it.endIso})"
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
}
