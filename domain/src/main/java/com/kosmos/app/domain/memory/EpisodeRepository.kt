package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus

/**
 * [EpisodeRepository]
 * 에피소드 문서(자동 분절·요약된 대화 단위)의 저장·조회 계약입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (Memory)
 * - **Dependencies**: [Episode]
 *
 * [WHY] 검색이 [KnowledgeRepository] 와 분리된 이유: 에피소드는 상태 기계·시간 범위·재시도
 * 카운트를 갖는 다른 형상이고, knowledge 테이블에 kind 판별자로 편입하면 기존 소비처(메모 목록·
 * 백업 내보내기)가 필터를 빠뜨릴 때 에피소드가 메모 UI 에 새는 무음 실패 모드가 생긴다.
 */
interface EpisodeRepository {
    suspend fun insert(episode: Episode): AppResult<Unit>
    suspend fun update(episode: Episode): AppResult<Unit>
    suspend fun getById(id: String): AppResult<Episode?>
    suspend fun getByStatus(status: EpisodeStatus): AppResult<List<Episode>>

    /** 아카이브 목록 — 최신순. UI 페이징(DefaultPagingSource 계약)과 호환. */
    suspend fun getEpisodes(offset: Int, limit: Int): AppResult<List<Episode>>

    /**
     * 본문(제목+요약) 어휘 검색. [query] 는 이스케이프 전 원문 — 구현이 SqlLike 를 적용한다.
     * SUMMARIZED 만 반환한다 (요약 전/실패 에피소드는 검색 대상이 아니다).
     */
    suspend fun search(query: String, limit: Int = 10): AppResult<List<Episode>>

    /** 태그 정확 매칭 (토큰 경계 강제). SUMMARIZED 만. */
    suspend fun searchByTags(tag: String, limit: Int = 10): AppResult<List<Episode>>

    suspend fun delete(id: String): AppResult<Unit>
}
