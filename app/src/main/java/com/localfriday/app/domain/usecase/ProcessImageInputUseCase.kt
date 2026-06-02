package com.localfriday.app.domain.usecase

import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.agent.AgentResult
import com.localfriday.app.runtime.gemma.ImageInputAdapter
import javax.inject.Inject

class ProcessImageInputUseCase @Inject constructor(
    private val imageAdapter: ImageInputAdapter,
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

        // 2. 바이트 배열을 Bitmap으로 디코딩
        val decodeResult = imageAdapter.decodeImage(rawImageBytes)
        if (decodeResult is AppResult.Failure) {
            return AppResult.Failure(decodeResult.error)
        }

        val bitmap = (decodeResult as AppResult.Success).data

        // 3. 리사이즈 및 JPEG 압축 전처리
        val processResult = imageAdapter.processImage(bitmap)
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
