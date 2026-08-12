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
    private val getTodayScheduleUseCase: GetTodayScheduleUseCase,
    private val summarizeScheduleUseCase: com.kosmos.app.domain.usecase.SummarizeScheduleUseCase
) : ToolExecutor {
    override val name: String = "GetSchedule"

    override suspend fun execute(args: ToolArguments, sessionId: String): String {
        // [WHY] date 는 없어도 오늘로 수렴하는 선택 인자다. optString 이 숫자·불린도 문자열로
        // 강제하므로, 모델이 따옴표를 빠뜨려도 조회 범위 판정이 조용히 틀어지지 않는다.
        //
        // [WHY] 이전에는 "week" 포함 여부만 보고 나머지를 전부 TODAY 로 떨어뜨렸다. PC 실험에서
        // "내일 스케줄 알려줘" 에 모델이 `date='tomorrow'` 를 보내는 것이 확인됐고, 그러면
        // **오늘 일정이 조용히 반환**됐다 — 틀린 답을 성공처럼 돌려주는 결함이다. 도메인이
        // TODAY/WEEK 두 범위만 지원하므로, 오늘이 아닌 값은 내일이 포함된 주간으로 넓힌다.
        val raw = args.optString("date")?.lowercase()?.trim()
        val range = when {
            raw.isNullOrEmpty() -> ScheduleData.RangeType.TODAY
            raw.contains("today") || raw.contains("오늘") -> ScheduleData.RangeType.TODAY
            else -> ScheduleData.RangeType.WEEK
        }
        val res = getTodayScheduleUseCase(range)
        return if (res is AppResult.Success) {
            val data = res.data
            // [WHY] 요약을 유스케이스에서 떼어낸 뒤에도 **툴 결과의 모양은 그대로 유지한다.**
            // 모델에게 주는 관측값을 바꾸면 툴 호출 실패(2026-08-12 실기기, 미해결) 조사에
            // 변수가 하나 더 늘어난다 — 그 조사가 끝난 뒤에 다룬다.
            val summary = (summarizeScheduleUseCase(data.events, range) as? AppResult.Success)?.data
            val text = buildString {
                append(summary?.takeIf { it.isNotBlank() } ?: "일정 요약이 없습니다.")
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
