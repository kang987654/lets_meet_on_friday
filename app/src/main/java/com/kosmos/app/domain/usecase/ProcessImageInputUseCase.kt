package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import javax.inject.Inject

class ProcessImageInputUseCase @Inject constructor(
    private val sendChatMessageUseCase: SendChatMessageUseCase
) {
    companion object {
        const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024L // 10MB
    }

    suspend operator fun invoke(
        sessionId: String,
        rawImageBytes: ByteArray
    ): AppResult<AgentResult> {
        // 1. 크기 검증
        if (rawImageBytes.size > MAX_IMAGE_SIZE_BYTES) {
            return AppResult.Failure(AppError.ImageTooLarge(rawImageBytes.size.toLong()))
        }

        // 2. 오케스트레이터로 전달 (텍스트는 기본 플레이스홀더 사용)
        // [WHY] JPEG 전처리는 SendChatMessageUseCase가 단일 지점에서 수행한다 —
        // 여기서 한 번 더 압축하면 이중 재인코딩으로 화질 열화와 CPU 낭비가 생긴다.
        return sendChatMessageUseCase(
            sessionId = sessionId,
            message = "[이미지 첨부됨]",
            imageBytes = rawImageBytes
        )
    }
}
