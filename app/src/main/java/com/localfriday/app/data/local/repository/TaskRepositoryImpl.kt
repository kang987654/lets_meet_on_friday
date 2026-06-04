package com.localfriday.app.data.local.repository

import androidx.paging.PagingSource
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.data.local.db.dao.TaskDao
import com.localfriday.app.data.local.db.entity.TaskEntity
import com.localfriday.app.domain.memory.TaskRepository
import com.localfriday.app.domain.model.TaskItem
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

    override fun getPendingTasks(): PagingSource<Int, TaskItem> {
        // TODO: Room의 PagingSource를 Domain Model로 변환하는 로직 필요
        throw NotImplementedError("getPendingTasks() needs map{} extension for PagingSource")
    }
}
