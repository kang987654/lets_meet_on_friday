package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.library.Int8Vector

/** 벡터를 만들 기억 하나 — 대상 키와 임베딩 입력 글자([MemoryEmbedding]). */
data class MemoryVectorInput(val key: MemoryVectorKey, val input: String)

/**
 * [MemoryVectorRepository]
 * 기억(메모·요약된 지난 대화)의 뜻 벡터 저장소 계약입니다 (0.41.3).
 *
 * ### Architecture Context
 * - **Layer**: Domain 계약 — 구현은 data 모듈(Room `memory_vector`). 무효화(메모·대화가 바뀌면 그 벡터 삭제)는 메모·대화 저장소가 맡는다.
 *
 * ### Key Flow
 * 1. [status] 로 준비 여부 → 검색 경로(결합 / 어휘)와 화면 표시. 2. [pending] → 임베딩 → [save]. 3. 검색은 [vectors] 를 한 번 읽어 내적.
 */
interface MemoryVectorRepository {
    suspend fun status(modelId: String?): AppResult<MemorySemanticStatus>

    /** 지금 모델의 벡터가 없는 기억 — 메모 먼저, 그다음 대화, 각각 최근 순. */
    suspend fun pending(modelId: String, limit: Int): AppResult<List<MemoryVectorInput>>

    /** 저장 — 다른 모델로 만든 벡터는 함께 지운다(모델 교체). */
    suspend fun save(modelId: String, vectors: List<Pair<MemoryVectorKey, Int8Vector>>): AppResult<Unit>

    suspend fun vectors(modelId: String): AppResult<Map<MemoryVectorKey, Int8Vector>>

    /** 모두 지운다 — 임베딩 모델을 지울 때. */
    suspend fun clear(): AppResult<Unit>
}
