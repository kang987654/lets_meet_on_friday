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
    suspend operator fun invoke(content: String, tags: List<String>): AppResult<KnowledgeNote> {
        // Generate embedding
        val embeddingResult = textEmbedder.embed(content)
        val embedding = if (embeddingResult is AppResult.Success) {
            embeddingResult.data
        } else {
            // Depending on policy, we might fail or proceed without embedding. For RAG, embedding is crucial.
            return AppResult.Failure((embeddingResult as AppResult.Failure).error)
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
