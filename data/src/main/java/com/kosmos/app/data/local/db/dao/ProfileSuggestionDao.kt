package com.kosmos.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kosmos.app.data.local.db.entity.ProfileSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileSuggestionDao {
    @Query("SELECT * FROM profile_suggestion WHERE status = :status ORDER BY createdAt")
    fun observeByStatus(status: String): Flow<List<ProfileSuggestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProfileSuggestionEntity)

    @Query("UPDATE profile_suggestion SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM profile_suggestion WHERE `key` = :key AND value = :value")
    suspend fun countByKeyValue(key: String, value: String): Int
}
