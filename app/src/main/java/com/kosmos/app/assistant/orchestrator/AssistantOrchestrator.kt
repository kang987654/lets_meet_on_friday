package com.kosmos.app.assistant.orchestrator

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.context.PromptAssembler
import com.kosmos.app.assistant.context.ResponseParser
import com.kosmos.app.assistant.context.ToolParser
import com.kosmos.app.assistant.guard.ExecutionPolicy
import com.kosmos.app.assistant.guard.PreExecutionGuard
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.ModelOutput
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.assistant.tool.ToolRegistry
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * [AssistantOrchestrator]
 * LLM 응답 처리 및 에이전트 라우팅을 담당하는 핵심 오케스트레이터 클래스입니다.
 *
 * 유저 입력을 받아 컨텍스트를 구성하고 모델을 실행한 뒤, 정책에 따라 적절한 액션(Agent)으로 분기합니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Orchestration)
 * - **Dependencies**: [ModelRunner], [ContextBuilder], [PromptAssembler], [PreExecutionGuard], [ToolRegistry]
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
        if (request.documentText != null) {
            createAndSaveMessage(request.sessionId, ChatMessage.Role.SYSTEM, request.documentText, InputType.TEXT)
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
