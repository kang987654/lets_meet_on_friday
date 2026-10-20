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

    /**
     * 일정([TaskItem.dueDateIso] 있는 행)을 뺀 남은 할 일 — 할 일 탭·위젯·브리핑 알림 개수용(0.38.3 M2b).
     * 모델 입력(아침 브리핑)·오늘 일정은 [getPendingTasksData] 를 쓴다. 기본 구현은 페이지를 거른 것이라
     * 페이징이 정확하지 않다 — 테스트 가짜용이고 실제 저장소는 쿼리로 재정의한다.
     */
    suspend fun getPendingTodosData(offset: Int, limit: Int): AppResult<List<TaskItem>> =
        getPendingTasksData(offset, limit).withoutSchedules()

    /** 일정을 뺀 완료한 할 일 — [getPendingTodosData] 와 같은 기준. */
    suspend fun getCompletedTodosData(offset: Int, limit: Int): AppResult<List<TaskItem>> =
        getCompletedTasksData(offset, limit).withoutSchedules()

    /** 할 일 한 건을 지웁니다. 리마인더 알람 취소는 호출측 몫(플랫폼 관심사). */
    suspend fun delete(taskId: String): AppResult<Unit>

    /** 완료한 할 일을 모두 지웁니다. */
    suspend fun deleteCompleted(): AppResult<Unit>

    /** 일정을 뺀 할 일의 미완료·완료 개수 — 페이징 목록과 무관한 통계용(0.38.3 M2b 부터 일정 제외). */
    suspend fun getCounts(): AppResult<TaskCounts>

    /** 아직 발화되지 않고 완료되지 않은 리마인더 전부 — 시각 정렬·과거 판정은 호출측 몫. */
    suspend fun getActiveReminders(): AppResult<List<TaskItem>>
    suspend fun markReminded(taskId: String, remindedAtMs: Long): AppResult<Unit>

    /**
     * 할 일 한 건의 현재 상태를 따라간다 — 채팅 결과 카드(0.38.2)가 미룸·울렸음·취소함을 바로 보이게.
     *
     * [WHY] 기본 구현은 한 번 조회해 내보낸다 — 테스트 가짜 저장소가 그대로 컴파일되게. Room 구현체가 흐름으로 덮어쓴다.
     */
    fun observeById(taskId: String): kotlinx.coroutines.flow.Flow<TaskItem?> = kotlinx.coroutines.flow.flow {
        emit((getById(taskId) as? AppResult.Success)?.data)
    }
}

private fun AppResult<List<TaskItem>>.withoutSchedules(): AppResult<List<TaskItem>> =
    if (this is AppResult.Success) AppResult.Success(data.filter { it.dueDateIso == null }) else this
