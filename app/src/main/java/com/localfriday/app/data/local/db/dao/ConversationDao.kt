package com.localfriday.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localfriday.app.data.local.db.entity.ConversationEntity

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversation WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentBySession(sessionId: String, limit: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversation WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedBySession(sessionId: String, offset: Int, limit: Int): List<ConversationEntity>
}
