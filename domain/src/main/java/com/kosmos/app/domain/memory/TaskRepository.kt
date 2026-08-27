package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.TaskItem

/**
 * [v1] 확장: 작업(Task) 관리 저장소
 * 시간 변환이나 Enum 매핑과 같은 mapper 책임은 구현체(Impl)에서 일관되게 처리해야 합니다.
 */
interface TaskRepository {
    suspend fun save(task: TaskItem): AppResult<Unit>
    suspend fun updateCompletion(taskId: String, isCompleted: Boolean): AppResult<Unit>
    suspend fun getPendingTasksData(offset: Int, limit: Int): AppResult<List<TaskItem>>
    suspend fun getById(taskId: String): AppResult<TaskItem?>

    /** 아직 발화되지 않고 완료되지 않은 리마인더 전부 — 시각 정렬·과거 판정은 호출측 몫. */
    suspend fun getActiveReminders(): AppResult<List<TaskItem>>
    suspend fun markReminded(taskId: String, remindedAtMs: Long): AppResult<Unit>
}
