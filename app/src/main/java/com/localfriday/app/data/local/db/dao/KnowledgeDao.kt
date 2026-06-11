package com.localfriday.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localfriday.app.data.local.db.entity.KnowledgeEntity

@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(knowledge: KnowledgeEntity)

    @Query("DELETE FROM knowledge_note WHERE id = :noteId")
    suspend fun delete(noteId: String)

    @Query("SELECT * FROM knowledge_note WHERE content LIKE '%' || :query || '%' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 100): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge_note ORDER BY createdAt DESC LIMIT :limit")
    suspend fun searchRecent(limit: Int): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge_note WHERE tags LIKE '%' || :tag || '%' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun searchByTags(tag: String, limit: Int): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge_note ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(offset: Int, limit: Int): List<KnowledgeEntity>
}
