package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.TextEmbedder
import com.kosmos.app.domain.model.KnowledgeNote
import java.util.UUID
import javax.inject.Inject

/**
 * [SaveKnowledgeUseCase]
 * 사용자의 기억이나 중요 정보(Memory)를 로컬 RAG 파이프라인에 저장하기 위한 핵심 유즈케이스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [KnowledgeRepository], [TextEmbedder]
 *
 * ### Key Flow
 * 1. 입력된 텍스트와 태그를 받습니다.
 * 2. [TextEmbedder]를 호출하여 텍스트를 고차원 부동소수점 배열(Vector)로 임베딩 변환합니다.
 * 3. 변환된 임베딩과 함께 UUID 기반의 새로운 [KnowledgeNote]를 생성하고 [KnowledgeRepository]를 통해 DB에 저장합니다.
 */
class SaveKnowledgeUseCase @Inject constructor(
    private val repository: KnowledgeRepository,
    private val textEmbedder: TextEmbedder
) {
    /**
     * [WHY] 임베딩 실패가 더 이상 **저장 자체를 막지 않는다.** 예전에는 실패 시 곧바로
     * Failure 를 반환했다("For RAG, embedding is crucial"). 그런데 지금 임베딩은 어떤 조회
     * 경로에서도 읽히지 않는다 — 임베더가 영어 전용이라 한국어 의미 검색이 무작위여서
     * 벡터 검색을 프로덕션에서 뺐기 때문이다(ADR-013). 읽히지 않는 값을 만들지 못했다는
     * 이유로 사용자의 "기억해줘"를 통째로 실패시키는 것은 명백히 잘못이다.
     *
     * [WHY] 그래도 계산은 계속한다. 한국어를 다루는 임베더로 자산만 갈아 끼우면 의미 검색이
     * 되살아나야 하는데, 여기서 배선을 끊어 두면 그때 조용히 썩어 있을 것이다.
     */
    suspend operator fun invoke(content: String, tags: List<String>): AppResult<KnowledgeNote> {
        val embedding = when (val embeddingResult = textEmbedder.embed(content)) {
            is AppResult.Success -> embeddingResult.data
            is AppResult.Failure -> null
        }

        val currentTime = System.currentTimeMillis()
        val note = KnowledgeNote(
            id = UUID.randomUUID().toString(),
            content = content,
            tags = tags,
            embedding = embedding,
            createdAt = currentTime,
            updatedAt = currentTime
        )

        val saveResult = repository.save(note)
        return if (saveResult is AppResult.Success) {
            AppResult.Success(note)
        } else {
            AppResult.Failure((saveResult as AppResult.Failure).error)
        }
    }
}
