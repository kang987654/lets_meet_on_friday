package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.KnowledgeNote

/**
 * [v1] 확장: 장기 메모리(지식) 관리 저장소
 * 시간 변환이나 Enum 매핑과 같은 mapper 책임은 구현체(Impl)에서 일관되게 처리해야 합니다.
 */
interface KnowledgeRepository {
    suspend fun save(note: KnowledgeNote): AppResult<Unit>
    suspend fun delete(noteId: String): AppResult<Unit>
    suspend fun search(query: String, limit: Int = 10): AppResult<List<KnowledgeNote>>
    suspend fun searchRecent(limit: Int = 10): AppResult<List<KnowledgeNote>>
    suspend fun searchByTags(tags: List<String>, limit: Int = 10): AppResult<List<KnowledgeNote>>
    suspend fun searchByVector(queryEmbedding: FloatArray, limit: Int = 3): AppResult<List<KnowledgeNote>>
    /** [WHY] `AuditRepository.getEvents` 와 같은 이유로 offset/limit 계약이다. */
    suspend fun getNotes(offset: Int, limit: Int): AppResult<List<KnowledgeNote>>
}
