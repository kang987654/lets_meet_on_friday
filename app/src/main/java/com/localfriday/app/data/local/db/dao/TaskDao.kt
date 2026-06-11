package com.localfriday.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localfriday.app.data.local.db.entity.TaskEntity

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Query("UPDATE task_item SET isCompleted = :isCompleted, completedAt = :completedAt WHERE id = :taskId")
    suspend fun updateCompletion(taskId: String, isCompleted: Boolean, completedAt: Long?)

    @Query("SELECT * FROM task_item WHERE isCompleted = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPendingTasks(offset: Int, limit: Int): List<TaskEntity>
}
