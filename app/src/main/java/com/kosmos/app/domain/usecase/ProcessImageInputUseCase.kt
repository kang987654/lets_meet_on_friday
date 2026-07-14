package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.tool.ImageProcessor
import javax.inject.Inject

class ProcessImageInputUseCase @Inject constructor(
    private val imageProcessor: ImageProcessor,
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

        // 2. 이미지 디코딩 및 전처리 (리사이즈 및 JPEG 압축)
        val processResult = imageProcessor.processImage(rawImageBytes)
        if (processResult is AppResult.Failure) {
            return AppResult.Failure(processResult.error)
        }

        val finalImageBytes = (processResult as AppResult.Success).data

        // 4. 오케스트레이터로 전달 (텍스트는 기본 플레이스홀더 사용)
        return sendChatMessageUseCase(
            sessionId = sessionId,
            message = "[이미지 첨부됨]",
            imageBytes = finalImageBytes
        )
    }
}
