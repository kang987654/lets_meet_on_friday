package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.dao.TaskDao
import com.kosmos.app.data.local.db.entity.TaskEntity
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.TaskItem

import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao
) : TaskRepository {

    override suspend fun save(task: TaskItem): AppResult<Unit> = dbWrite(TABLE, "save") {
        // [WHY] 도메인 TaskItem 에는 completedAt 이 없다 — 완료된 항목을 다시 save 하면(REPLACE)
        // 예전에는 완료 시각이 지금으로 덮였다. 기존 행의 값을 보존하고, 처음 완료될 때만 지금을 쓴다.
        val completedAt = if (task.isCompleted) {
            dao.getById(task.id)?.completedAt ?: System.currentTimeMillis()
        } else {
            null
        }
        dao.insert(
            TaskEntity(
                id = task.id,
                title = task.title,
                isCompleted = task.isCompleted,
                createdAt = task.createdAt,
                completedAt = completedAt,
                dueDateIso = task.dueDateIso,
                endDateIso = task.endDateIso,
                description = task.description,
                remindAtIso = task.remindAtIso,
                remindedAtMs = task.remindedAtMs,
                cancelledAt = task.cancelledAt
            )
        )
    }

    override suspend fun updateCompletion(taskId: String, isCompleted: Boolean): AppResult<Unit> = dbWrite(TABLE, "updateCompletion") {
        val completedAt = if (isCompleted) System.currentTimeMillis() else null
        dao.updateCompletion(taskId, isCompleted, completedAt)
    }

    override suspend fun getPendingTasksData(offset: Int, limit: Int): AppResult<List<TaskItem>> = dbRead(TABLE, "getPendingTasksData") {
        dao.getPendingTasks(offset, limit).map { it.toDomain() }
    }

    override suspend fun getById(taskId: String): AppResult<TaskItem?> = dbRead(TABLE, "getById") {
        dao.getById(taskId)?.toDomain()
    }

    override suspend fun getCompletedTasksData(offset: Int, limit: Int): AppResult<List<TaskItem>> = dbRead(TABLE, "getCompletedTasksData") {
        dao.getCompletedTasks(offset, limit).map { it.toDomain() }
    }

    override suspend fun getPendingTodosData(offset: Int, limit: Int): AppResult<List<TaskItem>> = dbRead(TABLE, "getPendingTodosData") {
        dao.getPendingTodos(offset, limit).map { it.toDomain() }
    }

    override suspend fun getCompletedTodosData(offset: Int, limit: Int): AppResult<List<TaskItem>> = dbRead(TABLE, "getCompletedTodosData") {
        dao.getCompletedTodos(offset, limit).map { it.toDomain() }
    }

    override suspend fun delete(taskId: String): AppResult<Unit> = dbWrite(TABLE, "delete") {
        dao.delete(taskId)
    }

    override suspend fun deleteCompleted(): AppResult<Unit> = dbWrite(TABLE, "deleteCompleted") {
        dao.deleteCompleted()
    }

    override suspend fun getCounts(): AppResult<com.kosmos.app.domain.memory.TaskCounts> = dbRead(TABLE, "getCounts") {
        com.kosmos.app.domain.memory.TaskCounts(pending = dao.countPending(), completed = dao.countCompleted())
    }

    override suspend fun getActiveReminders(): AppResult<List<TaskItem>> = dbRead(TABLE, "getActiveReminders") {
        dao.getActiveReminders().map { it.toDomain() }
    }

    override suspend fun markReminded(taskId: String, remindedAtMs: Long): AppResult<Unit> = dbWrite(TABLE, "markReminded") {
        dao.markReminded(taskId, remindedAtMs)
    }

    override fun observeById(taskId: String): kotlinx.coroutines.flow.Flow<TaskItem?> =
        dao.observeById(taskId).map { it?.toDomain() }

    private fun TaskEntity.toDomain(): TaskItem {
        return TaskItem(
            id = id,
            title = title,
            isCompleted = isCompleted,
            dueDateIso = dueDateIso,
            endDateIso = endDateIso,
            description = description,
            createdAt = createdAt,
            remindAtIso = remindAtIso,
            remindedAtMs = remindedAtMs,
            cancelledAt = cancelledAt
        )
    }

    private companion object {
        const val TABLE = "task_item"
    }
}
