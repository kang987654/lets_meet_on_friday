package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import javax.inject.Inject

/**
 * [SummarizeScheduleUseCase]
 * 일정 목록을 친절한 1~2문장 자연어 요약으로 만듭니다 (PRD F4 "일정 목록 + 요약 텍스트").
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [ModelRunner]
 *
 * [WHY] [GetTodayScheduleUseCase] 에서 떼어냈다. 조회는 수십 밀리초, 요약은 10초짜리 추론이다.
 * 한 반환값에 묶여 있던 동안 캘린더 화면은 목록조차 못 그리고 스피너만 돌았다.
 *
 * [WHY] 실패를 `null` 로 내리지 않고 [AppResult] 로 올린다. 호출자가 "요약이 아직 없음"과
 * "요약을 만들지 못함"을 구분할 수 있어야 화면이 자리를 비워 둘지 안내할지 정할 수 있다.
 */
class SummarizeScheduleUseCase @Inject constructor(
    private val modelRunner: ModelRunner
) {

    suspend operator fun invoke(
        events: List<CalendarEvent>,
        range: ScheduleData.RangeType
    ): AppResult<String> {
        if (events.isEmpty()) return AppResult.Success("")

        val eventListText = events.joinToString("\n") { "- ${it.title} (시간: ${it.startIso})" }
        val rangeStr = if (range == ScheduleData.RangeType.TODAY) "오늘" else "이번 주"
        val userInput = """
            다음은 사용자의 $rangeStr 일정입니다. 친절한 비서처럼 간결하게 1~2문장으로 요약해주세요.

            일정:
            $eventListText
        """.trimIndent()

        // [WHY] `oneShot = true` — 사용자 대화가 아니라 부수 계산이다. 붙이지 않으면 시스템 지시와
        // sessionId 가 채팅과 달라 런타임이 **캐시된 채팅 대화를 파괴**하고, 캘린더 화면을 열 때마다
        // 채팅이 다음 턴에 전체 프리필(툴 선언 ~2천 토큰 포함)을 다시 낸다 (ADR-010·014).
        val prompt = ChatPrompt(
            sessionId = SESSION_ID,
            systemInstruction = SYSTEM_INSTRUCTION,
            history = emptyList(),
            currentInput = userInput,
            oneShot = true
        )

        return when (val result = modelRunner.generate(prompt)) {
            is AppResult.Success -> AppResult.Success(result.data.text.trim())
            is AppResult.Failure -> AppResult.Failure(result.error)
        }
    }

    private companion object {
        const val SESSION_ID = "schedule-summary"
        const val SYSTEM_INSTRUCTION = "[System]\nYou are an assistant summarizing today's schedule."
    }
}
