package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.library.EmbedderProvider
import com.kosmos.app.domain.library.LibraryEmbedding
import com.kosmos.app.domain.memory.MemoryFusion
import com.kosmos.app.domain.memory.MemoryVectorKey
import com.kosmos.app.domain.memory.MemoryVectorRepository
import javax.inject.Inject

/**
 * [MemorySemanticRanker]
 * 기억 후보의 결합 순위(뜻 + 바이그램)입니다 (0.41.3 M3) — `search_memory` 와 서랍 검색이 같이 쓴다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Tool) — [EmbedderProvider](모델이 있을 때만), [MemoryVectorRepository](준비 판정·벡터), [MemoryFusion](식).
 *
 * ### Key Flow
 * 1. 모델이 없거나 기억 **전부**의 벡터가 준비되지 않았으면 null — 호출하는 쪽은 지금의 어휘 검색 그대로.
 * 2. 질의를 임베딩하고 후보(호출하는 쪽이 고른 기억 묶음)마다 내적 → `dense + 0.05 × sparse/최고점` 상위 [limit].
 *
 * [WHY] 결과 문구·회수 카드는 바꾸지 않고 후보·순위만 바꾼다(계획서 고정 제약) — 모델 입력 형식이 그대로라 기존 실측(회수 안내 문구 등)이 유효하다.
 */
class MemorySemanticRanker @Inject constructor(
    private val vectors: MemoryVectorRepository,
    private val embedders: EmbedderProvider
) {
    /**
     * @param query 검색어 — `search_memory` 는 모델이 뽑은 키워드, 서랍은 사용자가 친 글.
     * @param sparse 후보마다의 바이그램 점수(0 도 넣는다 — 후보 묶음이 곧 이 맵의 키다).
     */
    suspend fun rank(query: String, sparse: Map<MemoryVectorKey, Double>, limit: Int): List<MemoryVectorKey>? =
        rankScored(query, sparse, limit)?.map { it.first }

    /** [rank] 과 같되 결합 점수를 함께 — 서랍이 1위 근처만 남길 때 쓴다. */
    suspend fun rankScored(query: String, sparse: Map<MemoryVectorKey, Double>, limit: Int): List<Pair<MemoryVectorKey, Double>>? {
        if (query.isBlank() || sparse.isEmpty()) return null
        val embedder = embedders.current() ?: return null
        val status = (vectors.status(embedder.modelId) as? AppResult.Success)?.data ?: return null
        if (!status.ready) return null
        val q = embedder.embedQuery(LibraryEmbedding.queryInput(query)) ?: return null
        val stored = (vectors.vectorsFor(embedder.modelId, sparse.keys) as? AppResult.Success)?.data ?: return null
        val dense = sparse.keys.mapNotNull { key -> stored[key]?.let { key to it.dot(q).toDouble() } }.toMap()
        // [WHY] 준비 판정과 벡터 읽기 사이에 기억이 바뀌었으면(벡터가 모자람) 이번만 어휘로 — 일부만 dense 를 받으면 순위가 왜곡된다.
        if (dense.size < sparse.size) return null
        return MemoryFusion.scored(dense, sparse, limit)
    }
}
