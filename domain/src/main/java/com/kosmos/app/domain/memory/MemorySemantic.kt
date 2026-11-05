package com.kosmos.app.domain.memory

import com.kosmos.app.domain.library.LibraryFusion
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.KnowledgeNote

/**
 * 기억 벡터의 대상 — 메모 하나 또는 요약된 지난 대화 하나 (0.41.3).
 *
 * [WHY] 메모와 대화를 한 표(`memory_vector`)·한 순위에 둔다 — `search_memory` 가 둘을 함께 줄 세운다(ADR-022). 비교 가능해야 결합 순위가 결정적이다.
 */
data class MemoryVectorKey(val kind: Kind, val refId: String) : Comparable<MemoryVectorKey> {
    enum class Kind(val code: String) {
        NOTE("note"),
        EPISODE("episode");

        companion object {
            fun of(code: String): Kind? = entries.firstOrNull { it.code == code }
        }
    }

    override fun compareTo(other: MemoryVectorKey): Int = compareValuesBy(this, other, { it.kind.ordinal }, { it.refId })

    companion object {
        fun note(id: String) = MemoryVectorKey(Kind.NOTE, id)
        fun episode(id: String) = MemoryVectorKey(Kind.EPISODE, id)
    }
}

/**
 * [MemoryEmbedding]
 * 메모·지난 대화의 EmbeddingGemma 2 입력 글자입니다 (0.41.3).
 *
 * [WHY] 제목 자리에 태그를 넣는다 — exp80 에서 태그를 넣은 판이 뺀 판보다 나았다(보강 태그 결합 @3: 상위 개념 30 vs 27/30, 교차 언어 15 vs 13/15).
 * 질의는 문서와 같은 `LibraryEmbedding.queryInput`. 태그가 없으면 모델 카드대로 `none`.
 */
object MemoryEmbedding {
    fun noteInput(note: KnowledgeNote): String =
        "title: ${note.tags.joinToString(" ").trim().ifEmpty { NONE }} | text: ${note.content.trim()}"

    fun episodeInput(episode: Episode): String {
        val title = (listOfNotNull(episode.title?.trim()) + episode.tags).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { NONE }
        return "title: $title | text: ${episode.summary.orEmpty().trim()}"
    }

    private const val NONE = "none"
}

/**
 * [MemoryFusion]
 * 기억 후보의 결합 순위 — 문서와 같은 식 `dense + 0.05 × sparse/최고점`([LibraryFusion]).
 *
 * [WHY] 결합(C)으로 정했다(0.41.3 Q3, exp80·80b) — 재순위만(A)은 새 후보를 못 끌어와 교차 언어 이득이 0, 코사인 하한(B)은 답 없는 질문(최고 0.77)과 정답(하위 0.61)을
 * 가를 수 없었다. 결합은 상위 3 이 늘 차지만 모델 끝까지 재면 답 없는 질문의 지어내기가 늘지 않았다(L 2 = C 2).
 */
object MemoryFusion {
    fun rank(dense: Map<MemoryVectorKey, Double>, sparse: Map<MemoryVectorKey, Double>, limit: Int): List<MemoryVectorKey> =
        LibraryFusion.fuse(dense, sparse).take(limit).map { it.first }
}

/**
 * 기억의 뜻 검색 준비 상태 — 화면("준비 안 된 기억 n개")과 검색 경로 선택이 같이 쓴다.
 *
 * @param modelId 설치된 임베딩 모델 — null 이면 미설치
 * @param targets 벡터가 있어야 할 대상 수(메모 전부 + 요약된 지난 대화)
 * @param embedded 그중 지금 모델로 만든 벡터가 있는 수
 */
data class MemorySemanticStatus(val modelId: String?, val targets: Int, val embedded: Int) {
    /** 모자란 수 — 화면 표시용. */
    val pending: Int get() = (targets - embedded).coerceAtLeast(0)

    /**
     * 결합 검색을 써도 되는가 — 모델이 있고 대상 **전부**에 벡터가 있을 때만.
     *
     * [WHY] 일부만 벡터가 있으면 벡터 있는 기억만 dense 점수를 받아 순위가 왜곡된다(0.41.2 보관함과 같은 규칙). 새 기억은 저장 직후 만들어지므로(Q2)
     * 빈틈은 그 몇 초뿐이다.
     */
    val ready: Boolean get() = modelId != null && targets > 0 && embedded >= targets
}
