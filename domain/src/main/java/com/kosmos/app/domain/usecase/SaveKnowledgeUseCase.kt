package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.model.KnowledgeNote
import java.util.UUID
import javax.inject.Inject

/**
 * [SaveKnowledgeUseCase]
 * 사용자의 기억이나 중요 정보(Memory)를 메모로 저장하는 핵심 유즈케이스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [KnowledgeRepository]
 *
 * ### Key Flow
 * 1. 입력된 텍스트와 태그로 UUID 기반의 새 [KnowledgeNote] 를 만든다. 2. [KnowledgeRepository] 로 저장한다.
 *
 * [WHY] 저장 때 임베딩을 만들지 않는다 — 옛 영어 전용 임베더 호출(항상 실패하던 자리)은 0.41.3 에서 지웠다. 뜻 검색 벡터는 저장 뒤
 * 앱의 `MemoryEmbedder` 가 채팅이 끝나고 조용할 때 만든다(임베딩 엔진은 GPU 를 쓰므로 저장 경로에서 기다리지 않는다, ADR-038).
 */
class SaveKnowledgeUseCase @Inject constructor(
    private val repository: KnowledgeRepository
) {
    suspend operator fun invoke(
        content: String,
        tags: List<String>,
        source: String = KnowledgeNote.SOURCE_MANUAL
    ): AppResult<KnowledgeNote> {
        val currentTime = System.currentTimeMillis()
        val note = KnowledgeNote(
            id = UUID.randomUUID().toString(),
            content = content,
            tags = tags,
            createdAt = currentTime,
            updatedAt = currentTime,
            source = source
        )

        val saveResult = repository.save(note)
        return if (saveResult is AppResult.Success) {
            AppResult.Success(note)
        } else {
            AppResult.Failure((saveResult as AppResult.Failure).error)
        }
    }
}
