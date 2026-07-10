package com.kosmos.app.assistant.agent

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.CalendarDraft
import com.kosmos.app.domain.model.ModelOutput
import com.kosmos.app.domain.tool.CalendarTool
import javax.inject.Inject

/**
 * [CalendarAgent]
 * LLM이 파싱한 일정 생성 요청(CalendarDraft)을 실제 디바이스 캘린더에 연동하는 에이전트 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Agent Action)
 * - **Dependencies**: [CalendarTool] (디바이스 의존성)
 *
 * ### Key Flow
 * 1. Orchestrator로부터 모델 파싱 결과인 [ModelOutput.CalendarDraftOutput] 수신
 * 2. 도메인 모델([CalendarDraft])로 매핑
 * 3. [CalendarTool]을 호출하여 실제 안드로이드 캘린더 Provider에 Insert 수행
 */
class CalendarAgent @Inject constructor(
    private val calendarTool: CalendarTool
) {
    suspend fun executeCalendarInsert(action: ModelOutput.CalendarDraftOutput): AppResult<Long> {
        val draft = CalendarDraft(
            title = action.title,
            startIso = action.startIso,
            endIso = action.endIso,
            note = action.note,
            confidence = action.confidence
        )
        return calendarTool.insert(draft)
    }
}
