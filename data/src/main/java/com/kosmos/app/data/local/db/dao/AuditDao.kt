package com.kosmos.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.paging.PagingSource
import com.kosmos.app.data.local.db.entity.AuditEntity

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audit: AuditEntity)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC")
    fun getPaged(): PagingSource<Int, AuditEntity>
}
