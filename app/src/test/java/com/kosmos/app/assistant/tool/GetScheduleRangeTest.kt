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
        val executor = GetScheduleToolExecutor(useCase)
        val json = if (dateArg == null) JSONObject() else JSONObject().put("date", dateArg)

        runBlocking { executor.execute(ToolArguments(json), "s1") }
        return captured.captured
    }

    @Test
    fun `today 는 오늘 범위로 해석된다`() {
        assertEquals(ScheduleData.RangeType.TODAY, rangeFor("today"))
    }

    @Test
    fun `한국어 오늘도 오늘 범위로 해석된다`() {
        assertEquals(ScheduleData.RangeType.TODAY, rangeFor("오늘"))
    }

    @Test
    fun `date 가 없으면 오늘 범위다`() {
        assertEquals(ScheduleData.RangeType.TODAY, rangeFor(null))
    }

    @Test
    fun `week 는 주간 범위로 해석된다`() {
        assertEquals(ScheduleData.RangeType.WEEK, rangeFor("week"))
    }

    @Test
    fun `tomorrow 는 오늘이 아니라 주간으로 넓혀진다`() {
        // [WHY] 이것이 이 테스트의 핵심 회귀 방지다 — 예전에는 TODAY 로 떨어져 "내일"을 물으면
        // 오늘 일정이 나왔다. 도메인이 TODAY/WEEK 만 지원하므로 내일이 포함된 주간으로 넓힌다.
        assertEquals(ScheduleData.RangeType.WEEK, rangeFor("tomorrow"))
    }

    @Test
    fun `구체적 날짜 문자열도 주간으로 넓혀진다`() {
        assertEquals(ScheduleData.RangeType.WEEK, rangeFor("2026-08-09"))
    }

    @Test
    fun `대문자나 공백이 섞여도 판정이 흔들리지 않는다`() {
        assertEquals(ScheduleData.RangeType.TODAY, rangeFor("  TODAY "))
        assertEquals(ScheduleData.RangeType.WEEK, rangeFor(" Week "))
    }
}
