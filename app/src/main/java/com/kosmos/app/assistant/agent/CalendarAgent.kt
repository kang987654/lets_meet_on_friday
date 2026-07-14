package com.kosmos.app.assistant.agent

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.ModelOutput
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.TaskItem
import java.util.UUID
import javax.inject.Inject

/**
 * [CalendarAgent]
 * LLM이 파싱한 일정 생성 요청(CalendarDraft)을 내부 로컬 DB(TaskRepository)에 일정(Task)으로 연동하는 에이전트 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Agent Action)
 * - **Dependencies**: [TaskRepository] (로컬 DB 의존성)
 *
 * ### Key Flow
 * 1. Orchestrator로부터 모델 파싱 결과인 [ModelOutput.CalendarDraftOutput] 수신
 * 2. `startIso`를 단일 "일정 시간"으로 갖는 [TaskItem] 생성 (`endIso`는 완전히 생략)
 * 3. [TaskRepository]를 호출하여 로컬 DB에 저장
 */
class CalendarAgent @Inject constructor(
    private val taskRepository: TaskRepository
) {
    suspend fun executeCalendarInsert(action: ModelOutput.CalendarDraftOutput): AppResult<Long> {
        val taskItem = TaskItem(
            id = UUID.randomUUID().toString(),
            title = action.title + (action.note?.let { " - $it" } ?: ""),
            isCompleted = false,
            dueDateIso = action.startIso, // 시작시간을 단일 "일정 시간"으로 사용
            createdAt = System.currentTimeMillis()
        )
        return when (val result = taskRepository.save(taskItem)) {
            is AppResult.Success -> AppResult.Success(1L)
            is AppResult.Failure -> AppResult.Failure(result.error)
        }
    }
}
