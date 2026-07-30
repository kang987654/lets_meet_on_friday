package com.kosmos.app.assistant.orchestrator

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import java.util.UUID
import javax.inject.Inject

/**
 * [AssistantOrchestrator]
 * LLM 응답 처리 및 에이전트 라우팅을 담당하는 핵심 오케스트레이터 클래스입니다.
 *
 * 유저 입력을 받아 컨텍스트를 구성하고 모델을 실행한 뒤, 정책에 따라 적절한 액션(Agent)으로 분기합니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Orchestration)
 * - **Dependencies**: [ContextBuilder], [TaskRouter], [AuditTrailService], [ConversationRepository]
 *
 * ### Key Flow
 * 1. 유저 메시지 DB 저장
 * 2. [ContextBuilder]를 통한 대화 기록 로드 (Token Sliding Window 적용)
 * 3. [PromptAssembler]로 최종 프롬프트 조립
 * 4. [ModelRunner] 실행 및 JSON 결과 파싱 (내부 루프를 통해 Tool 재귀 실행)
 * 5. Guard 정책 검사 후 결과 반환
 */
class AssistantOrchestrator @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val contextBuilder: ContextBuilder,
    private val auditTrailService: AuditTrailService,
    private val taskRouter: TaskRouter
) {

    suspend fun processRequest(request: ChatRequest): AgentResult {
        // 1. 유저 메시지 저장
        val content = if (request.audioFilePath != null && request.message.isBlank()) "(음성 메시지)" else request.message
        val inputType = if (request.audioFilePath != null) InputType.VOICE else if (request.imageBytes != null) InputType.IMAGE else InputType.TEXT
        val saveUserResult = createAndSaveMessage(request.sessionId, ChatMessage.Role.USER, content, inputType)
        if (saveUserResult is AppResult.Failure) {
            return AgentResult.Error(saveUserResult.error)
        }

        // 2. 문서 텍스트 저장
        // [WHY] 첨부 문서를 SYSTEM 역할로 저장하면 문서 안의 지시문이 시스템 프롬프트로 승격되어
        // 세션 내내 지속되는 프롬프트 인젝션 경로가 된다. USER 역할의 구분된 블록으로 저장한다.
        if (request.documentText != null) {
            val documentBlock = "[Attached Document]\n\"\"\"\n${request.documentText}\n\"\"\""
            val saveDocResult = createAndSaveMessage(request.sessionId, ChatMessage.Role.USER, documentBlock, InputType.TEXT)
            if (saveDocResult is AppResult.Failure) {
                return AgentResult.Error(saveDocResult.error)
            }
        }

        // 3. 컨텍스트 구성
        val contextResult = contextBuilder.build(request.sessionId)
        val context = when (contextResult) {
            is AppResult.Success -> contextResult.data
            is AppResult.Failure -> return handleErrorAndReturn(request.sessionId, "대화 문맥을 구성하지 못했습니다: ${contextResult.error}")
        }

        // 4. 라우팅 및 에이전트 위임
        val agent = taskRouter.route(request.message)
        return agent.execute(request, context)
    }

    private suspend fun handleErrorAndReturn(sessionId: String, errorMsg: String): AgentResult.Error {
        auditTrailService.logError(sessionId, errorMsg)
        createAndSaveMessage(sessionId, ChatMessage.Role.ASSISTANT, errorMsg, InputType.TEXT)
        return AgentResult.Error(com.kosmos.app.core.common.AppError.ModelInferenceError(errorMsg))
    }

    private suspend fun createAndSaveMessage(
        sessionId: String, 
        role: ChatMessage.Role, 
        content: String, 
        inputType: InputType,
        searchUsed: Boolean = false, 
        thinkingProcess: String? = null
    ): AppResult<Unit> {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            inputType = inputType,
            searchUsed = searchUsed,
            createdAt = System.currentTimeMillis(),
            thinkingProcess = thinkingProcess
        )
        return conversationRepository.save(message)
    }
}
