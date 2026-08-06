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
 * 유저 입력을 저장하고 컨텍스트를 구성해 에이전트에 위임하는 핵심 오케스트레이터입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Orchestration)
 * - **Dependencies**: [ContextBuilder], [AuditTrailService], [ConversationRepository]
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
    private val agent: com.kosmos.app.assistant.agent.KosmosAgent
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

        // 4. 에이전트 위임
        // [WHY] 사전 라우팅을 폐지했다 — 툴 선택은 모델이 런타임 함수호출로 직접 한다.
        // 키워드 기반 라우팅은 분류가 틀리면 툴이 사라지는 실패 모드를 만들었다 (ADR-008).
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
