package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import javax.inject.Inject

class GetScheduleToolExecutor @Inject constructor(
    private val getTodayScheduleUseCase: GetTodayScheduleUseCase
) : ToolExecutor {
    override val name: String = "GetSchedule"

    override suspend fun execute(args: Map<String, Any>, sessionId: String): String {
        val range = if ((args["date"] as? String)?.lowercase()?.contains("week") == true) {
            ScheduleData.RangeType.WEEK
        } else {
            ScheduleData.RangeType.TODAY
        }
        val res = getTodayScheduleUseCase(range)
        return if (res is AppResult.Success) {
            val data = res.data
            val text = buildString {
                append(data.summary ?: "일정 요약이 없습니다.")
                append("\n\n")
                data.events.forEach { event ->
                    append("- ${event.title} (${event.startIso})\n")
                }
            }
            "{\"status\": \"success\", \"data\": \"$text\"}"
        } else {
            "{\"status\": \"error\", \"message\": \"일정 조회 실패\"}"
        }
    }
}
