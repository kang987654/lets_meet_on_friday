package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.SqlLike
import com.kosmos.app.core.common.Tags
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.data.local.db.dao.EpisodeDao
import com.kosmos.app.data.local.db.entity.EpisodeEntity
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import javax.inject.Inject

/**
 * [EpisodeRepositoryImpl]
 * 에피소드 문서의 Room 저장·조회 구현입니다.
 *
 * ### Architecture Context
 * - **Layer**: Data (Repository)
 * - **Dependencies**: [EpisodeDao]
 *
 * ### Key Flow
 * 1. 도메인 [Episode] ↔ [EpisodeEntity] 매핑 (status enum ↔ 문자열, tags List ↔ CSV).
 * 2. 검색어는 여기서 `SqlLike.escape`, 태그는 `Tags.normalize` 를 적용해 DAO 계약을 지킨다.
 */
class EpisodeRepositoryImpl @Inject constructor(
    private val episodeDao: EpisodeDao
) : EpisodeRepository {

    override suspend fun insert(episode: Episode): AppResult<Unit> = write("에피소드 저장") {
        episodeDao.insert(episode.toEntity())
    }

    override suspend fun update(episode: Episode): AppResult<Unit> = write("에피소드 갱신") {
        // [WHY] REPLACE insert 라 update 와 같다 — id 가 PK 이므로 별도 UPDATE 쿼리가 필요 없다.
        episodeDao.insert(episode.toEntity())
    }

    override suspend fun getById(id: String): AppResult<Episode?> = read("에피소드 조회") {
        episodeDao.getById(id)?.toDomain()
    }

    override suspend fun getByStatus(status: EpisodeStatus): AppResult<List<Episode>> =
        read("상태별 조회") { episodeDao.getByStatus(status.name).map { it.toDomain() } }

    override suspend fun getEpisodes(offset: Int, limit: Int): AppResult<List<Episode>> =
        read("아카이브 조회") { episodeDao.getEpisodes(offset, limit).map { it.toDomain() } }

    override suspend fun search(query: String, limit: Int): AppResult<List<Episode>> {
        // [WHY] 빈 질의를 조회 전에 차단한다 — '%%' 는 전체 매칭이 된다 (KnowledgeRepositoryImpl 전례).
        if (query.isBlank()) return AppResult.Success(emptyList())
        return read("에피소드 검색") {
            episodeDao.search(SqlLike.escape(query.trim()), limit).map { it.toDomain() }
        }
    }

    override suspend fun searchByTags(tag: String, limit: Int): AppResult<List<Episode>> {
        val normalized = Tags.normalize(tag)
        if (normalized.isEmpty()) return AppResult.Success(emptyList())
        return read("태그 검색") {
            episodeDao.searchByTags(SqlLike.escape(normalized), limit).map { it.toDomain() }
        }
    }

    override suspend fun delete(id: String): AppResult<Unit> = write("에피소드 삭제") {
        episodeDao.delete(id)
    }

    private inline fun <T> read(what: String, block: () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.e("EpisodeRepo", "$what 실패", e)
        AppResult.Failure(AppError.DbReadError("episode"))
    }

    private inline fun write(what: String, block: () -> Unit): AppResult<Unit> = try {
        block()
        AppResult.Success(Unit)
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.e("EpisodeRepo", "$what 실패", e)
        AppResult.Failure(AppError.DbWriteError("episode"))
    }
}

internal fun Episode.toEntity(): EpisodeEntity = EpisodeEntity(
    id = id,
    sessionId = sessionId,
    status = status.name,
    title = title,
    summary = summary,
    tags = tags.joinToString(","),
    startAt = startAt,
    endAt = endAt,
    messageCount = messageCount,
    retryCount = retryCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun EpisodeEntity.toDomain(): Episode = Episode(
    id = id,
    sessionId = sessionId,
    status = try { EpisodeStatus.valueOf(status) } catch (e: Exception) { EpisodeStatus.FAILED },
    title = title,
    summary = summary,
    tags = tags.split(",").mapNotNull { it.trim().ifEmpty { null } },
    startAt = startAt,
    endAt = endAt,
    messageCount = messageCount,
    retryCount = retryCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)
