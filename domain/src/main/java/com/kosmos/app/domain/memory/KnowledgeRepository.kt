package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.KnowledgeNote

/**
 * [v1] 확장: 장기 메모리(지식) 관리 저장소
 * 시간 변환이나 Enum 매핑과 같은 mapper 책임은 구현체(Impl)에서 일관되게 처리해야 합니다.
 *
 * [WHY] 벡터 검색(`searchByVector`)은 0.41.3 에서 지웠다 — 옛 영어 전용 임베더(ADR-013·026)의 빈 자리였다. 뜻 검색은
 * [MemoryVectorRepository] 의 벡터와 `SearchMemoryToolExecutor` 의 결합 순위가 맡는다(ADR-038).
 */
interface KnowledgeRepository {
    suspend fun save(note: KnowledgeNote): AppResult<Unit>
    suspend fun delete(noteId: String): AppResult<Unit>

    /**
     * 메모 한 건 — 채팅 결과 카드(0.38.2)가 저장·근거 메모의 현재 내용을 보인다. 없으면(지웠으면) null.
     *
     * [WHY] 기본 구현은 "없음" — 테스트 가짜 저장소가 그대로 컴파일되게. Room 구현체가 덮어쓴다.
     */
    suspend fun getById(noteId: String): AppResult<KnowledgeNote?> = AppResult.Success(null)
    suspend fun search(query: String, limit: Int = 10): AppResult<List<KnowledgeNote>>
    suspend fun searchRecent(limit: Int = 10): AppResult<List<KnowledgeNote>>
    suspend fun searchByTags(tags: List<String>, limit: Int = 10): AppResult<List<KnowledgeNote>>
    /** [WHY] `AuditRepository.getEvents` 와 같은 이유로 offset/limit 계약이다. */
    suspend fun getNotes(offset: Int, limit: Int): AppResult<List<KnowledgeNote>>
}
