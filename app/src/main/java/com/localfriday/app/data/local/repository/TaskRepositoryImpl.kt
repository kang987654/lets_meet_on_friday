package com.localfriday.app.data.local.repository

import androidx.paging.PagingSource
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.data.local.db.dao.TaskDao
import com.localfriday.app.data.local.db.entity.TaskEntity
import com.localfriday.app.domain.memory.TaskRepository
import com.localfriday.app.domain.model.TaskItem
import kotlinx.coroutines.flow.map
import androidx.paging.map
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao
) : TaskRepository {

    override suspend fun save(task: TaskItem): AppResult<Unit> = runCatching {
        dao.insert(
            TaskEntity(
                id = task.id,
                title = task.title,
                isCompleted = task.isCompleted,
                createdAt = task.createdAt,
                completedAt = if (task.isCompleted) System.currentTimeMillis() else null
            )
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Failure(com.localfriday.app.core.common.AppError.DbWriteError("task_item")) }
    )

    override suspend fun updateCompletion(taskId: String, isCompleted: Boolean): AppResult<Unit> = runCatching {
        val completedAt = if (isCompleted) System.currentTimeMillis() else null
        dao.updateCompletion(taskId, isCompleted, completedAt)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Failure(com.localfriday.app.core.common.AppError.DbWriteError("task_item")) }
    )

    override fun getPendingTasksData(): kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<TaskItem>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = 20),
            pagingSourceFactory = { dao.getPendingTasks() }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
    }

    private fun TaskEntity.toDomain(): TaskItem {
        return TaskItem(
            id = id,
            title = title,
            isCompleted = isCompleted,
            dueDateIso = null, // V1 DB에 아직 없음
            createdAt = createdAt
        )
    }
}
