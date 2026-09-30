package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [GetScheduleRangeTest]
 * 모델이 보낸 `date` 값이 어떤 조회 범위로 해석되는지 고정합니다.
 *
 * [WHY] 이전 구현은 `"week"` 포함 여부만 보고 나머지를 전부 TODAY 로 떨어뜨렸다. PC 실험에서
 * "내일 스케줄 알려줘" 에 모델이 `date='tomorrow'` 를, "모레 뭐 있지?" 에 `date='2026-08-09'`
 * 를 보내는 것이 확인됐고, 그러면 **오늘 일정이 조용히 반환**됐다 — 틀린 답을 성공처럼
 * 돌려주는 결함이라 사용자가 알아채기 어렵다. 툴 설명으로 값을 좁혔지만 모델 출력은 보장할 수
 * 없으므로 실행부에서도 막는다.
 */
@RunWith(RobolectricTestRunner::class)
class GetScheduleRangeTest {

    private fun rangeFor(dateArg: String?): ScheduleData.RangeType {
        val captured = slot<ScheduleData.RangeType>()
        val useCase: GetTodayScheduleUseCase = mockk {
            coEvery { this@mockk.invoke(capture(captured)) } returns AppResult.Success(
                ScheduleData(
                    events = kotlinx.collections.immutable.persistentListOf(),
                    summary = "없음",
                    rangeType = ScheduleData.RangeType.TODAY
                )
            )
        }
        // 이 테스트는 범위 해석만 본다 — 요약은 관심사가 아니므로 빈 문자열로 둔다.
        val summarize: com.kosmos.app.domain.usecase.SummarizeScheduleUseCase = mockk {
            coEvery { this@mockk.invoke(any(), any()) } returns AppResult.Success("")
        }
        val executor = GetScheduleToolExecutor(useCase, summarize)
        val json = if (dateArg == null) JSONObject() else JSONObject().put("date", dateArg)

        runBlocking { executor.execute(ToolArguments(json), "s1") }
        return captured.captured
    }

    // [WHY] 표 형식 — 입력만 다른 사례 7개를 하나로 모았다(0.27.x 정리, 사용자 확인). 각 사례의 뜻은 주석으로 남긴다.
    @Test
    fun `date 인자가 조회 범위로 해석되는 규칙`() {
        val today = ScheduleData.RangeType.TODAY
        val week = ScheduleData.RangeType.WEEK
        val cases = listOf(
            "today" to today,
            "오늘" to today,              // 한국어
            null to today,               // date 가 없으면 오늘
            "week" to week,
            // [WHY] 이 테스트의 핵심 회귀 방지 — 예전에는 TODAY 로 떨어져 "내일"을 물으면 오늘 일정이
            // 나왔다. 도메인이 TODAY/WEEK 만 지원하므로 내일이 포함된 주간으로 넓힌다.
            "tomorrow" to week,
            "2026-08-09" to week,        // 구체적 날짜도 주간으로
            "  TODAY " to today,         // 대소문자·공백이 섞여도 흔들리지 않는다
            " Week " to week,
        )
        cases.forEach { (arg, expected) ->
            assertEquals("date=[$arg]", expected, rangeFor(arg))
        }
    }
}
