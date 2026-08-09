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

    /**
     * 코사인 유사도 기반 의미 검색입니다.
     *
     * **현재 프로덕션에서 호출되지 않습니다.** 앱이 싣고 있는 임베더
     * (`universal_sentence_encoder.tflite`)가 영어 전용이라 한국어에서는 결과가 무작위이기
     * 때문입니다 — PC 실측(ADR-013): 서로 무관한 한국어 문장 8개의 쌍별 코사인이 0.93~1.00
     * (평균 0.964)으로 한 점에 뭉치고(같은 내용의 영어 문장은 0.63~0.82), 관련쌍과 무관쌍의
     * 분리도가 **0.000**, 실제 검색 정확도는 top-1 1/7 로 무작위(1/8)와 같았다.
     *
     * 지우지 않고 남겨 둔 이유는 **막힌 곳이 외부 자산 하나**이기 때문이다. 한국어를 다루는
     * 임베더로 교체하면 이 경로와 [KnowledgeNote.embedding] 저장은 그대로 되살아난다.
     * 그때까지 기억 조회는 `SearchMemory` 툴(어휘 검색)이 맡는다. **다시 배선하기 전에
     * 반드시 임베더의 한국어 분별력을 먼저 재라.**
     */
    suspend fun searchByVector(queryEmbedding: FloatArray, limit: Int = 3): AppResult<List<KnowledgeNote>>
    /** [WHY] `AuditRepository.getEvents` 와 같은 이유로 offset/limit 계약이다. */
    suspend fun getNotes(offset: Int, limit: Int): AppResult<List<KnowledgeNote>>
}
