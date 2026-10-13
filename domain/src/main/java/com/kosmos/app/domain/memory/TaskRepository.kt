package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.TaskItem

/** 할 일 통계 — [TaskRepository.getCounts]. */
data class TaskCounts(val pending: Int, val completed: Int) {
    val total: Int get() = pending + completed
}

/**
 * [v1] 확장: 작업(Task) 관리 저장소
 * 시간 변환이나 Enum 매핑과 같은 mapper 책임은 구현체(Impl)에서 일관되게 처리해야 합니다.
 */
interface TaskRepository {
    suspend fun save(task: TaskItem): AppResult<Unit>
    suspend fun updateCompletion(taskId: String, isCompleted: Boolean): AppResult<Unit>
    suspend fun getPendingTasksData(offset: Int, limit: Int): AppResult<List<TaskItem>>
    suspend fun getById(taskId: String): AppResult<TaskItem?>

    /** 완료한 할 일 — 완료 시각 최신순. 기억 화면의 "완료됨" 묶음용. */
    suspend fun getCompletedTasksData(offset: Int, limit: Int): AppResult<List<TaskItem>>

    /** 할 일 한 건을 지웁니다. 리마인더 알람 취소는 호출측 몫(플랫폼 관심사). */
    suspend fun delete(taskId: String): AppResult<Unit>

    /** 완료한 할 일을 모두 지웁니다. */
    suspend fun deleteCompleted(): AppResult<Unit>

    /** 전체 할 일의 미완료·완료 개수 — 페이징 목록과 무관한 통계용. */
    suspend fun getCounts(): AppResult<TaskCounts>

    /** 아직 발화되지 않고 완료되지 않은 리마인더 전부 — 시각 정렬·과거 판정은 호출측 몫. */
    suspend fun getActiveReminders(): AppResult<List<TaskItem>>
    suspend fun markReminded(taskId: String, remindedAtMs: Long): AppResult<Unit>
}
