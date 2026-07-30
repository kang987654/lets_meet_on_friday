package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.dao.TaskDao
import com.kosmos.app.data.local.db.entity.TaskEntity
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.TaskItem

import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao
) : TaskRepository {

    override suspend fun save(task: TaskItem): AppResult<Unit> = com.kosmos.app.core.common.runCatchingCancellable {
        dao.insert(
            TaskEntity(
                id = task.id,
                title = task.title,
                isCompleted = task.isCompleted,
                createdAt = task.createdAt,
                completedAt = if (task.isCompleted) System.currentTimeMillis() else null,
                dueDateIso = task.dueDateIso,
                endDateIso = task.endDateIso,
                description = task.description
            )
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.DbWriteError("task_item")) }
    )

    override suspend fun updateCompletion(taskId: String, isCompleted: Boolean): AppResult<Unit> = com.kosmos.app.core.common.runCatchingCancellable {
        val completedAt = if (isCompleted) System.currentTimeMillis() else null
        dao.updateCompletion(taskId, isCompleted, completedAt)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.DbWriteError("task_item")) }
    )

    override suspend fun getPendingTasksData(offset: Int, limit: Int): AppResult<List<TaskItem>> = com.kosmos.app.core.common.runCatchingCancellable {
        dao.getPendingTasks(offset, limit).map { it.toDomain() }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(com.kosmos.app.core.common.AppError.DbReadError("task_item")) }
    )

    private fun TaskEntity.toDomain(): TaskItem {
        return TaskItem(
            id = id,
            title = title,
            isCompleted = isCompleted,
            dueDateIso = dueDateIso,
            endDateIso = endDateIso,
            description = description,
            createdAt = createdAt
        )
    }
}
