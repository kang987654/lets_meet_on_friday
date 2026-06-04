package com.localfriday.app.domain.usecase

import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.agent.AgentResult
import com.localfriday.app.domain.assistant.orchestrator.AssistantOrchestrator
import com.localfriday.app.domain.assistant.orchestrator.ChatRequest
import javax.inject.Inject

class SendChatMessageUseCase @Inject constructor(
    private val assistantOrchestrator: AssistantOrchestrator
) {
    companion object {
        const val MAX_INPUT_CHARS = 8192
    }

    suspend operator fun invoke(
        sessionId: String, 
        message: String,
        imageBytes: ByteArray? = null,
        onToken: ((String) -> Unit)? = null
    ): AppResult<AgentResult> {
        val trimmedMessage = message.trim()

        // 1. 유효성 검사 (빈 문자열)
        if (trimmedMessage.isBlank()) {
            return AppResult.Failure(AppError.ValidationError("message", "메시지를 입력해주세요."))
        }

        // 2. 유효성 검사 (길이 제한)
        if (trimmedMessage.length > MAX_INPUT_CHARS) {
            return AppResult.Failure(AppError.ValidationError("message", "입력 가능한 최대 글자 수($MAX_INPUT_CHARS)를 초과했습니다."))
        }

        // 3. Orchestrator 위임
        val request = ChatRequest(
            sessionId = sessionId,
            message = trimmedMessage,
            imageBytes = imageBytes,
            onToken = onToken
        )
        
        return try {
            val result = assistantOrchestrator.processRequest(request)
            AppResult.Success(result)
        } catch (e: Exception) {
            AppResult.Failure(AppError.ModelInferenceError(e.message ?: "알 수 없는 오류가 발생했습니다."))
        }
    }
}
