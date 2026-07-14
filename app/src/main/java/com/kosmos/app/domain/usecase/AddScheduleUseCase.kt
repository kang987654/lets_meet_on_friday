package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.TaskItem
import java.util.UUID
import javax.inject.Inject

/**
 * [AddScheduleUseCase]
 * 사용자 입력이나 에이전트의 결정에 따라 새로운 일정(Task)을 로컬 DB에 추가하는 유스케이스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [TaskRepository]
 *
 * ### Key Flow
 * 1. 전달받은 일정 제목, 시작/종료 시간, 설명을 기반으로 [TaskItem] 도메인 모델 생성
 * 2. [TaskRepository]를 호출하여 Room DB에 저장
 * 3. 저장 성공 여부를 [AppResult]로 반환하여 UI 또는 Orchestrator에서 후속 처리(예: 응답 메시지 생성) 수행
 */
class AddScheduleUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(title: String, startTime: String, endTime: String, description: String?): AppResult<Long> {
        val taskItem = TaskItem(
            id = UUID.randomUUID().toString(),
            title = title + (description?.let { " - $it" } ?: ""),
            isCompleted = false,
            dueDateIso = startTime,
            createdAt = System.currentTimeMillis()
        )
        return when (val result = taskRepository.save(taskItem)) {
            is AppResult.Success -> AppResult.Success(1L)
            is AppResult.Failure -> AppResult.Failure(result.error)
        }
    }
}
