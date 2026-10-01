package com.kosmos.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kosmos.app.data.local.db.entity.EpisodeEntity

/**
 * [WHY] 0.31.0 에서 interface → abstract class — 에피소드 통합([mergeInto])이 conversation·episode 두 테이블을 **한 트랜잭션**으로
 * 바꿔야 하는데, Room 의 `@Transaction` 본문 메서드는 abstract class DAO 에서 가장 확실하게 지원된다.
 */
@Dao
abstract class EpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(episode: EpisodeEntity)

    @Query("DELETE FROM episode WHERE id = :episodeId")
    abstract suspend fun delete(episodeId: String)

    @Query("SELECT * FROM episode WHERE id = :episodeId")
    abstract suspend fun getById(episodeId: String): EpisodeEntity?

    @Query("SELECT * FROM episode WHERE status = :status ORDER BY createdAt ASC")
    abstract suspend fun getByStatus(status: String): List<EpisodeEntity>

    /**
     * 아카이브 목록 — 요약이 완성된 문서만.
     * [WHY] OPEN/CLOSED 는 제목이 없고(요약 전) FAILED 는 미노출 계약이다 — 원문은 타임라인에
     * 그대로 있으므로 사용자가 잃는 것은 없다.
     */
    @Query("SELECT * FROM episode WHERE status = 'SUMMARIZED' ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    abstract suspend fun getEpisodes(offset: Int, limit: Int): List<EpisodeEntity>

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
    abstract suspend fun search(query: String, limit: Int = 10): List<EpisodeEntity>

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
    abstract suspend fun searchByTags(tag: String, limit: Int = 10): List<EpisodeEntity>

    @Query("UPDATE conversation SET episodeId = :toId WHERE episodeId = :fromId")
    protected abstract suspend fun reassignMessages(fromId: String, toId: String)

    /**
     * 회수 칩 id 재매핑 — 저장 형식은 공백 없는 쉼표 목록(ConversationRepositoryImpl).
     * [WHY] 양끝에 쉼표를 붙여 경계 일치로 치환한다 — 다른 id 의 부분 문자열을 건드리지 않게. LIKE 대신 instr 을 쓴다(id 의 `_` 가 와일드카드가 되지 않게).
     */
    @Query(
        "UPDATE conversation SET recallEpisodeIds = " +
            "TRIM(REPLACE(',' || recallEpisodeIds || ',', ',' || :fromId || ',', ',' || :toId || ','), ',') " +
            "WHERE instr(',' || recallEpisodeIds || ',', ',' || :fromId || ',') > 0"
    )
    protected abstract suspend fun remapRecallIds(fromId: String, toId: String)

    /**
     * [fromId] 에피소드를 [into] 로 합칩니다 — 메시지 재배정 · 회수 칩 재매핑 · 원본 삭제 · 대상 갱신을 한 트랜잭션으로 (0.31.0).
     * 중간에 실패하면 전부 롤백되어 메시지가 어느 에피소드에도 속하지 않는 상태가 생기지 않는다.
     */
    @Transaction
    open suspend fun mergeInto(fromId: String, into: EpisodeEntity) {
        reassignMessages(fromId, into.id)
        remapRecallIds(fromId, into.id)
        delete(fromId)
        insert(into)
    }
}
