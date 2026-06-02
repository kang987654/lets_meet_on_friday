package com.localfriday.app.domain.memory

import androidx.paging.PagingSource
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.model.TaskItem

/**
 * [v1] 확장: 작업(Task) 관리 저장소
 * 시간 변환이나 Enum 매핑과 같은 mapper 책임은 구현체(Impl)에서 일관되게 처리해야 합니다.
 */
interface TaskRepository {
    suspend fun save(task: TaskItem): AppResult<Unit>
    suspend fun updateCompletion(taskId: String, isCompleted: Boolean): AppResult<Unit>
    fun getPendingTasks(): PagingSource<Int, TaskItem>
}
