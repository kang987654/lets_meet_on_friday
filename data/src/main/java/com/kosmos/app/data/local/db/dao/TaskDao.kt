package com.kosmos.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kosmos.app.data.local.db.entity.TaskEntity

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Query("UPDATE task_item SET isCompleted = :isCompleted, completedAt = :completedAt WHERE id = :taskId")
    suspend fun updateCompletion(taskId: String, isCompleted: Boolean, completedAt: Long?)

    @Query("SELECT * FROM task_item WHERE isCompleted = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPendingTasks(offset: Int, limit: Int): List<TaskEntity>

    @Query("SELECT * FROM task_item WHERE id = :taskId")
    suspend fun getById(taskId: String): TaskEntity?

    // [WHY] 기억 화면 통계("남은 일 N · 완료 M")는 전체 기준이어야 한다 — 페이징 목록은
    // 미완료만, 그것도 로드된 페이지만 담아서 거기서 세면 완료 수가 늘 0이었다.
    @Query("SELECT COUNT(*) FROM task_item WHERE isCompleted = 0")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM task_item WHERE isCompleted = 1")
    suspend fun countCompleted(): Int

    // [WHY] remindAtIso 가 TEXT(ISO)라 SQL 시각 비교를 하지 않는다 — 시각 파싱·정렬은
    // Kotlin 몫이다 (GetTodayScheduleUseCase 전례). 개인 1인 규모라 전량 조회로 충분하다.
    @Query("SELECT * FROM task_item WHERE remindAtIso IS NOT NULL AND remindedAtMs IS NULL AND isCompleted = 0")
    suspend fun getActiveReminders(): List<TaskEntity>

    @Query("UPDATE task_item SET remindedAtMs = :remindedAtMs WHERE id = :taskId")
    suspend fun markReminded(taskId: String, remindedAtMs: Long)
}
