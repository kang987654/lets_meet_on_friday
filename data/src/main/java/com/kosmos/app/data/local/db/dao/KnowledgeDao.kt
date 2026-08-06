package com.kosmos.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kosmos.app.data.local.db.entity.KnowledgeEntity

@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(knowledge: KnowledgeEntity)

    @Query("DELETE FROM knowledge_note WHERE id = :noteId")
    suspend fun delete(noteId: String)

    /**
     * [query]는 호출부에서 `SqlLike.escape`로 이스케이프된 값이어야 합니다.
     * [WHY] ESCAPE 절이 없으면 검색어에 포함된 `%`/`_`가 와일드카드로 해석돼
     * "100%" 검색이 "100" 포함 전체를, "%" 검색이 테이블 전체를 매칭한다.
     */
    @Query("SELECT * FROM knowledge_note WHERE content LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 100): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge_note ORDER BY createdAt DESC LIMIT :limit")
    suspend fun searchRecent(limit: Int): List<KnowledgeEntity>

    /**
     * 태그를 정확히 한 개 단위로 매칭합니다.
     * [WHY] tags는 "work,urgent" 형태 콤마 문자열이므로 단순 부분 일치는 "work"가
     * "workflow"에도 걸린다. 양쪽을 구분자로 감싸 토큰 경계를 강제한다.
     */
    @Query("SELECT * FROM knowledge_note WHERE ',' || tags || ',' LIKE '%,' || :tag || ',%' ESCAPE '\\' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun searchByTags(tag: String, limit: Int): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge_note ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getNotes(offset: Int, limit: Int): List<KnowledgeEntity>
}
