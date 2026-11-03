package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [GetScheduleRangeTest]
 * 모델이 보낸 `date` 값이 어떤 조회 범위로 해석되는지와 결과 형식(종료 시각·캘린더 실패·절단)을 고정합니다.
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
                    events = persistentListOf(),
                    rangeType = ScheduleData.RangeType.TODAY
                )
            )
        }
        val executor = GetScheduleToolExecutor(useCase)
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

    @Test
    fun `결과 형식 - 시작과 종료 시각·빈 일정·기기 캘린더 실패 안내·토큰 상한 절단`() = runBlocking {
        // 1. 일반 일정 (시작~종료 시각 포함)
        val eventUseCase: GetTodayScheduleUseCase = mockk {
            coEvery { this@mockk.invoke(any()) } returns AppResult.Success(
                ScheduleData(
                    events = persistentListOf(
                        CalendarEvent(
                            id = "e1",
                            title = "팀 회의",
                            startIso = "2026-08-20T10:00:00",
                            endIso = "2026-08-20T11:00:00"
                        )
                    ),
                    rangeType = ScheduleData.RangeType.TODAY
                )
            )
        }
        val normalData = JSONObject(
            GetScheduleToolExecutor(eventUseCase).execute(ToolArguments(JSONObject()), "s1")
        ).getString("data")
        assertTrue(normalData.startsWith("[일정]\n- 팀 회의 (8월 20일 오전 10:00 ~ 오전 11:00)"))
        assertFalse("중첩 요약 문구가 없어야 한다", normalData.contains("일정 요약"))

        // 2. 빈 일정 + 기기 캘린더 읽기 실패(EC4)
        val emptyFailedUseCase: GetTodayScheduleUseCase = mockk {
            coEvery { this@mockk.invoke(any()) } returns AppResult.Success(
                ScheduleData(
                    events = persistentListOf(),
                    rangeType = ScheduleData.RangeType.TODAY,
                    deviceCalendarFailed = true
                )
            )
        }
        val emptyFailedData = JSONObject(
            GetScheduleToolExecutor(emptyFailedUseCase).execute(ToolArguments(JSONObject()), "s1")
        ).getString("data")
        assertTrue(emptyFailedData.startsWith("등록된 일정이 없어요."))
        assertTrue("일정이 0건이어도 기기 캘린더 실패 안내가 포함돼야 한다", emptyFailedData.contains("기기 캘린더를 읽지 못해"))

        // 3. 다량 일정 — 토큰 상한(400) 절단 표식
        val manyEvents = (1..80).map { i ->
            CalendarEvent(
                id = "e$i",
                title = "반복 프로젝트 점검 회의 $i",
                startIso = "2026-08-20T10:00:00",
                endIso = "2026-08-20T11:00:00"
            )
        }
        val manyUseCase: GetTodayScheduleUseCase = mockk {
            coEvery { this@mockk.invoke(any()) } returns AppResult.Success(
                ScheduleData(
                    events = persistentListOf(*manyEvents.toTypedArray()),
                    rangeType = ScheduleData.RangeType.WEEK
                )
            )
        }
        val cappedData = JSONObject(
            GetScheduleToolExecutor(manyUseCase).execute(ToolArguments(JSONObject().put("date", "week")), "s1")
        ).getString("data")
        assertTrue(cappedData.endsWith("... [TRUNCATED TO SAVE CONTEXT]"))
    }
}
