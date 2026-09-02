package com.kosmos.app.data.embedder

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.TextEmbedder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DisabledTextEmbedder]
 * 임베더가 없는 상태를 나타내는 [TextEmbedder] 구현 — 항상 실패를 돌려준다 (C2, 0.26.0).
 *
 * ### Architecture Context
 * - **Layer**: Data (Embedder)
 * - **Consumers**: `SaveKnowledgeUseCase`(실패를 null 임베딩으로 흡수)
 *
 * [WHY] MediaPipe 임베더(영어 전용, 한국어 분별력 0 — ADR-013)를 구현·의존·자산째 제거했다(APK 약 28MB).
 * **계약 층은 남긴다**: [TextEmbedder]·`KnowledgeNote.embedding`·`searchByVector` 는 기존 테스트 25건이
 * 단언으로 고정하고 있고(단언 수정 0건 규칙), 한국어 임베더(C3)를 도입할 때 되살릴 자리다.
 * 그래서 인터페이스를 지우는 대신 "항상 실패"를 바인딩한다 — `SaveKnowledgeUseCase` 는 임베딩 실패를
 * 저장 실패로 올리지 않으므로(0.10.x 부터, 테스트가 증명) 사용자 경로는 달라지지 않는다. ADR-026.
 */
@Singleton
class DisabledTextEmbedder @Inject constructor() : TextEmbedder {
    override suspend fun embed(text: String): AppResult<FloatArray> =
        AppResult.Failure(AppError.SearchError("텍스트 임베더 없음 — C2 제거(ADR-026)"))
}
