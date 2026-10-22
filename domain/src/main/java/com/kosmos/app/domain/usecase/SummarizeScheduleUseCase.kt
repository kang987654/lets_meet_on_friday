package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.util.IsoDateTimeParser
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

        // [WHY] 시각은 한국어 표기로 넣는다 — ISO 를 주면 "시각 원문 보존" 규칙대로 ISO 를 그대로 옮겨 적었다(exp54 c4 1/5 → c5 0/5, AGENTS §4-7).
        val eventListText = events.joinToString("\n") {
            "- ${it.title} (시간: ${IsoDateTimeParser.toDisplayKorean(it.startIso) ?: it.startIso})"
        }
        val rangeStr = if (range == ScheduleData.RangeType.TODAY) "오늘" else "이번 주"
        val userInput = "다음은 사용자의 $rangeStr 일정입니다.\n\n일정:\n$eventListText"

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
        // [WHY] oneShot 공통 형식 — 요약 요청을 재료에서 지시로 옮기고 해요체로(exp54 c5: 해요체 5/5, 일정 이름 빠짐 0, ISO 0).
        val SYSTEM_INSTRUCTION = """
            [System]
            You are a personal assistant summarizing the user's schedule in Korean.
            당신은 사용자의 일정을 요약하는 개인 비서입니다. 아래 규칙을 지키세요.
            - 일정을 1~2문장의 해요체로 간결히 요약하세요.
            - 시각·이름은 일정의 원문 그대로 보존하세요.
        """.trimIndent()
    }
}
