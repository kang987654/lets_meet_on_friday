package com.localfriday.app.domain.assistant.orchestrator

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.agent.AgentResult
import com.localfriday.app.domain.assistant.audit.AuditTrailService
import com.localfriday.app.domain.assistant.context.ContextBuilder
import com.localfriday.app.domain.assistant.context.PromptAssembler
import com.localfriday.app.domain.assistant.context.ResponseParser
import com.localfriday.app.domain.assistant.guard.ExecutionPolicy
import com.localfriday.app.domain.assistant.guard.PreExecutionGuard
import com.localfriday.app.domain.memory.ConversationRepository
import com.localfriday.app.domain.model.ChatMessage
import com.localfriday.app.domain.model.InputType
import com.localfriday.app.domain.model.ModelOutput
import com.localfriday.app.domain.modelrunner.ModelRunner
import java.util.UUID
import javax.inject.Inject

class AssistantOrchestrator @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val intentClassifier: IntentClassifier,
    private val contextBuilder: ContextBuilder,
    private val promptAssembler: PromptAssembler,
    private val modelRunner: ModelRunner,
    private val responseParser: ResponseParser,
    private val preExecutionGuard: PreExecutionGuard,
    private val auditTrailService: AuditTrailService
) {

    suspend fun processRequest(request: ChatRequest): AgentResult {
        // 1. 유저 메시지 저장
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = request.sessionId,
            role = ChatMessage.Role.USER,
            content = request.message,
            inputType = InputType.TEXT,
            createdAt = System.currentTimeMillis()
        )
        val saveUserRes = conversationRepository.save(userMessage)
        if (saveUserRes is AppResult.Failure) {
            return AgentResult.Error(saveUserRes.error)
        }

        // 2. 의도 분류 (v0에서는 로직 분기보다 힌트로만 사용됨)
        val intent = intentClassifier.classify(request.message)

        // 3. 컨텍스트 구성
        val contextRes = contextBuilder.build(request.sessionId)
        val context = when (contextRes) {
            is AppResult.Success -> contextRes.data
            is AppResult.Failure -> return AgentResult.Error(contextRes.error)
        }

        // 4. 프롬프트 조립
        val prompt = promptAssembler.assemble(context, request.message)

        // 5. LLM 추론 실행
        val modelRes = modelRunner.generate(prompt)
        val rawOutput = when (modelRes) {
            is AppResult.Success -> modelRes.data
            is AppResult.Failure -> {
                auditTrailService.logError(request.sessionId, "Model execution failed: ${modelRes.error}")
                return AgentResult.Error(modelRes.error)
            }
        }
        
        // 런타임 결과 로깅
        auditTrailService.logModelRun(request.sessionId, prompt, rawOutput)

        // 6. 결과 파싱
        val modelOutput = responseParser.parse(rawOutput)

        // 7. 정책 가드 확인
        val policy = preExecutionGuard.checkPolicy(modelOutput)

        // 8. 정책 적용 및 반환
        return when (policy) {
            is ExecutionPolicy.Allowed -> {
                val text = if (modelOutput is ModelOutput.TextOutput) modelOutput.content else rawOutput
                saveAssistantMessage(request.sessionId, text)
                AgentResult.Text(text)
            }
            is ExecutionPolicy.RequiresApproval -> {
                // 승인 대기 상태. 메시지는 사용자가 승인/거부한 후에 저장됨.
                AgentResult.ActionRequired(modelOutput)
            }
            is ExecutionPolicy.Blocked -> {
                // v1 안내 문구를 일반 답변처럼 제공
                saveAssistantMessage(request.sessionId, policy.reason)
                AgentResult.Text(policy.reason)
            }
        }
    }

    private suspend fun saveAssistantMessage(sessionId: String, content: String) {
        val assistantMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = ChatMessage.Role.ASSISTANT,
            content = content,
            inputType = InputType.TEXT,
            createdAt = System.currentTimeMillis()
        )
        conversationRepository.save(assistantMessage)
    }
}
