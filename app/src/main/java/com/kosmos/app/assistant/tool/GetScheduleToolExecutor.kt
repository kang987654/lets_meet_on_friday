package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.common.CurrentLanguage
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.domain.tool.ToolNames
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.domain.util.SentenceTruncator
import javax.inject.Inject

/**
 * [GetScheduleToolExecutor]
 * 모델의 `GetSchedule` 툴 콜을 받아 오늘/이번 주 일정을 조회하는 실행기입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Tool)
 * - **Dependencies**: [GetTodayScheduleUseCase], [Tokenizer]
 *
 * ### Key Flow
 * 1. `date` 인자("today"/"week")로 조회 범위를 결정합니다.
 * 2. 조회된 일정을 시작·종료 시각과 함께 직렬화하고, 기기 캘린더 읽기 실패(EC4)·주간 범위 날짜 판별 안내를 덧붙여 토큰 상한 안에서 JSON 문자열로 반환합니다.
 *
 * [WHY] 예전에는 툴 실행 도중에 `SummarizeScheduleUseCase`(oneShot 부수 추론)를 한 번 더 불렀다.
 * 채팅 추론이 어차피 목록을 보고 최종 답을 다시 쓰므로 요약 추론 1회(약 5~10초)와 `oneShot` 뒤 대화 복원(약 3.4초)이 중복 지불됐고,
 * 일정 0건일 때도 "일정 요약이 없습니다."만 보내 기기 캘린더 실패 여부를 빠뜨렸다 (0.40.6 M2, exp73 17/17).
 */
class GetScheduleToolExecutor @Inject constructor(
    private val getTodayScheduleUseCase: GetTodayScheduleUseCase,
    private val tokenizer: Tokenizer,
    private val currentLanguage: CurrentLanguage = CurrentLanguage.KO
) : ToolExecutor {

    override val name: String = ToolNames.GET_SCHEDULE

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
        val lang = currentLanguage.current
        val en = lang == AppLanguage.EN
        val res = getTodayScheduleUseCase(range)
        return if (res is AppResult.Success) {
            val data = res.data
            val body = if (data.events.isEmpty()) {
                if (en) "No scheduled events." else "등록된 일정이 없어요."
            } else {
                val header = if (en) "[Schedule]" else "[일정]"
                val lines = data.events.map { event ->
                    // [WHY] ISO 원문 대신 한국어/영어 시작·종료 범위 표기("8월 20일 오후 4:00 ~ 오후 5:00" / "Aug 20, 4:00 PM ~ 5:00 PM")를 준다 —
                    // 모델이 툴 결과를 그대로 인용하므로 원문을 주면 답변에 ISO 가 노출되고(2026-08-15), 시작 시각만 주면 종료 시각 질문에 답할 수 없다(0.40.6 M2).
                    val display = IsoDateTimeParser
                        .toDisplayRangeKorean(event.startIso, event.endIso, lang = lang) ?: event.startIso
                    "- ${event.title} ($display)"
                }
                header + "\n" + lines.joinToString("\n")
            }
            val weekTail = if (data.events.isNotEmpty() && range == ScheduleData.RangeType.WEEK) {
                if (en) WEEK_TAIL_EN else WEEK_TAIL_KO
            } else {
                ""
            }
            val calNote = if (data.deviceCalendarFailed) {
                if (en) CAL_FAIL_TAIL_EN else CAL_FAIL_TAIL_KO
            } else {
                ""
            }
            // [WHY] 목록만 자르고 꼬리 안내는 그 뒤에 붙인다 — 안내까지 한 덩어리로 자르면 일정이 많은 주에 날짜 대조·캘린더 실패 안내가 먼저 잘려 나간다.
            val tails = weekTail + calNote
            val capped = SentenceTruncator.truncate(
                text = body,
                maxTokens = Constants.TOOL_RESULT_MAX_TOKENS - Constants.TOOL_RESULT_ENVELOPE_RESERVE_TOKENS -
                    tokenizer.sizeInTokens(tails),
                tokenizer = tokenizer,
                marker = TRUNCATION_MARKER
            ) + tails
            // [WHY] 일정 제목에 따옴표/개행이 있어도 JSON이 깨지지 않도록 JSONObject로 조립한다.
            ToolResultJson.success().put("data", capped).toString()
        } else {
            ToolResultJson.error(if (en) "Failed to load schedule" else "일정 조회 실패")
        }
    }

    private companion object {
        const val TRUNCATION_MARKER = "\n\n... [TRUNCATED TO SAVE CONTEXT]"

        // [WHY] 주간 범위(내일·모레·특정일 질의 포함)는 여러 날 일정이 함께 내려가므로, 데이터 끝에 날짜 대조 안내를 둔다 —
        // 없으면 내일 일정이 비어 있을 때 모레 일정을 내일 일정으로 오인하거나 덧붙인다 (exp73 schedule 14/17 → 17/17).
        const val WEEK_TAIL_KO =
            "\n이 목록에서 사용자가 물은 날짜와 일치하는 일정만 골라 답하고, 그 날짜에 일정이 없으면 없다고만 답하고 다른 날짜 일정은 말하지 마세요."
        const val WEEK_TAIL_EN =
            "\nOnly report events whose date matches the date the user asked about; if there is no event on that date, state only that nothing is scheduled for that date and do not mention events on other dates."

        // [WHY] 기기 캘린더 읽기 실패 안내도 데이터 끝에 둔다(SearchWikipedia·SearchMemory 전례) — 앞에 두면 빈 일정·영문 회신에서 안내를 빠뜨린다 (exp73 cal_fail 4/4).
        const val CAL_FAIL_TAIL_KO =
            "\n\n(주의: 기기 캘린더를 읽지 못해 앱에 저장된 일정만 조회했습니다. 답변에 기기 캘린더를 확인하지 못했다는 점을 꼭 함께 알려주세요.)"
        const val CAL_FAIL_TAIL_EN =
            "\n\n(Note: Could not read the device calendar, so only app-saved events were checked. Be sure to mention in your reply that the device calendar could not be checked.)"
    }
}
