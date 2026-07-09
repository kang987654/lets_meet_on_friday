package com.localfriday.app.domain.usecase

import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.agent.AgentResult
import com.localfriday.app.assistant.orchestrator.AssistantOrchestrator
import com.localfriday.app.assistant.orchestrator.ChatRequest
import javax.inject.Inject

/**
 * [SendChatMessageUseCase]
 * 사용자 입력(또는 텍스트/이미지 결합 데이터)을 받아 채팅 메시지 처리 파이프라인을 시작하는 UseCase입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [AssistantOrchestrator]
 *
 * ### Key Flow
 * 1. 사용자 입력의 유효성(빈 문자열, 최대 글자 수 초과 등) 검사
 * 2. 유효한 입력에 한해 [ChatRequest] 객체 생성
 * 3. [AssistantOrchestrator]로 요청을 위임하여 모델 추론 파이프라인 실행
 * 4. 파이프라인의 최종 결과([AgentResult])를 반환
 */
class SendChatMessageUseCase @Inject constructor(
    private val assistantOrchestrator: AssistantOrchestrator,
    private val conversationRepository: com.localfriday.app.domain.memory.ConversationRepository
) {
    companion object {
        const val MAX_INPUT_CHARS = 8192
    }

    suspend operator fun invoke(
        sessionId: String, 
        message: String,
        imageBytes: ByteArray? = null,
        documentText: String? = null,
        audioFilePath: String? = null,
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
            documentText = documentText,
            audioFilePath = audioFilePath,
            onToken = onToken
        )
        
        return try {
            val result = assistantOrchestrator.processRequest(request)
            AppResult.Success(result)
        } catch (e: Exception) {
            val errorMsg = "알 수 없는 오류가 발생했습니다: ${e.message}"
            
            // 시스템 에러 메시지(Assistant 역할)를 DB에 강제로 저장하여 턴 동기화 보장
            val errorMessageToSave = com.localfriday.app.domain.model.ChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = com.localfriday.app.domain.model.ChatMessage.Role.ASSISTANT,
                content = errorMsg,
                inputType = com.localfriday.app.domain.model.InputType.TEXT,
                createdAt = System.currentTimeMillis()
            )
            conversationRepository.save(errorMessageToSave)

            AppResult.Failure(AppError.ModelInferenceError(e.message ?: "알 수 없는 오류가 발생했습니다."))
        }
    }
}
