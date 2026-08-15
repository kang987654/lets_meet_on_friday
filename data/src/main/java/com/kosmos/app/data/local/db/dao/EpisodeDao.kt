package com.kosmos.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kosmos.app.data.local.db.entity.EpisodeEntity

@Dao
interface EpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(episode: EpisodeEntity)

    @Query("DELETE FROM episode WHERE id = :episodeId")
    suspend fun delete(episodeId: String)

    @Query("SELECT * FROM episode WHERE id = :episodeId")
    suspend fun getById(episodeId: String): EpisodeEntity?

    @Query("SELECT * FROM episode WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: String): List<EpisodeEntity>

    @Query("SELECT * FROM episode ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getEpisodes(offset: Int, limit: Int): List<EpisodeEntity>

    /**
     * [query]는 호출부에서 `SqlLike.escape`로 이스케이프된 값이어야 합니다.
     * [WHY] ESCAPE 절이 없으면 검색어의 `%`/`_`가 와일드카드로 해석된다 — KnowledgeDao 와
     * 동일한 계약. 검색 대상은 요약이 완성된 문서뿐이다(SUMMARIZED).
     */
    @Query(
        "SELECT * FROM episode WHERE status = 'SUMMARIZED' AND " +
            "(title LIKE '%' || :query || '%' ESCAPE '\\' OR summary LIKE '%' || :query || '%' ESCAPE '\\') " +
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int = 10): List<EpisodeEntity>

    /**
     * 태그를 정확히 한 개 단위로 매칭합니다.
     * [WHY] 양쪽을 구분자로 감싸 토큰 경계를 강제한다 — "work"가 "workflow"에 걸리지 않게
     * (KnowledgeDao.searchByTags 와 동일한 패턴).
     */
    @Query(
        "SELECT * FROM episode WHERE status = 'SUMMARIZED' AND " +
            "',' || tags || ',' LIKE '%,' || :tag || ',%' ESCAPE '\\' " +
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun searchByTags(tag: String, limit: Int = 10): List<EpisodeEntity>
}
