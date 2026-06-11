package com.localfriday.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localfriday.app.data.local.db.entity.AuditEntity

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audit: AuditEntity)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(offset: Int, limit: Int): List<AuditEntity>

    @Query("SELECT * FROM audit_log WHERE eventType = :eventType ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedByType(eventType: String, offset: Int, limit: Int): List<AuditEntity>
}
