package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import org.json.JSONObject
import javax.inject.Inject

/**
 * [GetScheduleToolExecutor]
 * 모델의 `GetSchedule` 툴 콜을 받아 오늘/이번 주 일정을 조회하는 실행기입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Tool)
 * - **Dependencies**: [GetTodayScheduleUseCase]
 *
 * ### Key Flow
 * 1. `date` 인자("today"/"week")로 조회 범위를 결정합니다.
 * 2. 조회 결과를 JSON(JSONObject 이스케이프 적용) 문자열로 반환합니다.
 */
class GetScheduleToolExecutor @Inject constructor(
    private val getTodayScheduleUseCase: GetTodayScheduleUseCase
) : ToolExecutor {
    override val name: String = "GetSchedule"

    override suspend fun execute(args: ToolArguments, sessionId: String): String {
        // [WHY] date 는 없어도 오늘로 수렴하는 선택 인자다. optString 이 숫자·불린도 문자열로
        // 강제하므로, 모델이 따옴표를 빠뜨려도 조회 범위 판정이 조용히 틀어지지 않는다.
        val range = if (args.optString("date")?.lowercase()?.contains("week") == true) {
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
            // [WHY] 일정 제목에 따옴표/개행이 있어도 JSON이 깨지지 않도록 JSONObject로 조립한다.
            JSONObject().put("status", "success").put("data", text).toString()
        } else {
            JSONObject().put("status", "error").put("message", "일정 조회 실패").toString()
        }
    }
}
