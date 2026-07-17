package com.kosmos.app.assistant.agent

import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.context.ResponseParser
import com.kosmos.app.assistant.context.ToolParser
import com.kosmos.app.assistant.guard.ExecutionPolicy
import com.kosmos.app.assistant.guard.PreExecutionGuard
import com.kosmos.app.assistant.orchestrator.ChatRequest
import com.kosmos.app.assistant.tool.ToolRegistry
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.ModelOutput
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * [BaseAgent]
 * 에이전트의 공통 실행 로직(Tool Loop, Context 관리, 상태 반환)을 정의하는 추상 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant Agent
 * - **Dependencies**: [ModelRunner], [ToolRegistry], [PreExecutionGuard]
 *
 * ### Key Flow
 * 1. 하위 클래스에서 정의된 Prompt를 기반으로 추론을 시작합니다.
 * 2. Tool Call이 발생하면 파싱하여 도구를 실행하고, 그 결과를 컨텍스트에 추가하여 재귀적으로 추론합니다.
 * 3. 최종적으로 도출된 텍스트 응답을 [AgentResult]로 감싸서 반환합니다.
 */
abstract class BaseAgent(
    protected val modelRunner: ModelRunner,
    protected val toolRegistry: ToolRegistry,
    protected val responseParser: ResponseParser,
    protected val preExecutionGuard: PreExecutionGuard,
    protected val auditTrailService: AuditTrailService,
    protected val conversationRepository: ConversationRepository
) {
    abstract suspend fun execute(request: ChatRequest, context: ContextBuilder.Context): AgentResult
    
    protected abstract fun assemblePrompt(context: ContextBuilder.Context, userInput: String): ChatPrompt

    protected suspend fun executeToolLoop(request: ChatRequest, initialPrompt: ChatPrompt): AgentResult = coroutineScope {
        var prompt = initialPrompt
        var rawOutput = ""
        var parsedResult: ToolParser.ParsedStream? = null
        var loopCount = 0
        val MAX_TOOL_LOOP_COUNT = 3

        val scope = this
        while (true) {
            if (loopCount >= MAX_TOOL_LOOP_COUNT) {
                auditTrailService.logError(request.sessionId, "Max tool loop count exceeded")
                return@coroutineScope AgentResult.Error(AppError.ModelInferenceTimeout(30000L))
            }
            loopCount++
            
            var toolCallDetected = false
            var accumulatedToken = ""
            val wrappedOnToken: (String) -> Unit = { token ->
                accumulatedToken += token
                val p = ToolParser.parseStream(accumulatedToken)
                if (p.toolCalls.isNotEmpty() && !toolCallDetected) {
                    toolCallDetected = true
                    scope.launch { modelRunner.cancel() }
                }
                request.onToken?.invoke(token)
            }

            val modelResult = if (request.audioFilePath != null && rawOutput.isEmpty()) {
                modelRunner.generateWithAudio(prompt, request.audioFilePath, wrappedOnToken)
            } else if (request.imageBytes != null && rawOutput.isEmpty()) {
                modelRunner.generateWithImage(prompt, request.imageBytes, request.imageTokenBudget, wrappedOnToken)
            } else {
                modelRunner.generate(prompt, wrappedOnToken)
            }

            rawOutput = when (modelResult) {
                is AppResult.Success -> modelResult.data
                is AppResult.Failure -> return@coroutineScope handleErrorAndReturn(request.sessionId, "Model inference failed: ${modelResult.error.toString()}")
            }

            parsedResult = ToolParser.parseStream(rawOutput)

            if (parsedResult!!.toolCalls.isNotEmpty()) {
                val call = parsedResult!!.toolCalls.first() // 병렬 처리 방지
                val toolJson = executeToolInner(call, request.sessionId)
                val newInput = "${prompt.currentInput}\n$rawOutput\n<tool_response>\n$toolJson\n</tool_response>"
                prompt = prompt.copy(currentInput = newInput)
                continue
            }
            break
        }

        if (parsedResult == null) {
            return@coroutineScope handleErrorAndReturn(request.sessionId, "Model execution failed")
        }

        auditTrailService.logModelRun(request.sessionId, prompt.currentInput, rawOutput)

        val modelOutput = responseParser.parse(parsedResult!!.content)

        return@coroutineScope applyPolicyAndReturn(request.sessionId, modelOutput, parsedResult!!)
    }

    private suspend fun executeToolInner(call: ToolParser.ToolCallData, sessionId: String): String {
        val executor = toolRegistry.getExecutor(call.name)
        return if (executor != null) {
            executor.execute(call.args, sessionId)
        } else {
            "{\"status\": \"error\", \"message\": \"알 수 없는 Tool입니다: ${call.name}\"}"
        }
    }

    private suspend fun applyPolicyAndReturn(
        sessionId: String, 
        modelOutput: ModelOutput, 
        parsed: ToolParser.ParsedStream
    ): AgentResult {
        return when (val policy = preExecutionGuard.checkPolicy(modelOutput)) {
            is ExecutionPolicy.Allowed -> {
                if (modelOutput is ModelOutput.CalendarDraftOutput) {
                    val actionCard = com.kosmos.app.domain.model.ActionCard(
                        actionType = com.kosmos.app.domain.model.ActionCard.ActionType.CALENDAR_DRAFT,
                        payload = com.kosmos.app.domain.model.ActionPayload.CalendarDraftPayload(modelOutput.draft)
                    )
                    AgentResult.Action(actionCard)
                } else {
                    val text = if (modelOutput is ModelOutput.TextOutput) modelOutput.content else parsed.content
                    createAndSaveMessage(sessionId, ChatMessage.Role.ASSISTANT, text, InputType.TEXT, searchUsed = false, thinkingProcess = parsed.thinking)
                    AgentResult.Text(text, thinkingProcess = parsed.thinking)
                }
            }
            is ExecutionPolicy.Blocked -> {
                createAndSaveMessage(sessionId, ChatMessage.Role.ASSISTANT, policy.reason, InputType.TEXT)
                AgentResult.Text(policy.reason)
            }
        }
    }

    private suspend fun handleErrorAndReturn(sessionId: String, errorMsg: String): AgentResult.Error {
        auditTrailService.logError(sessionId, errorMsg)
        createAndSaveMessage(sessionId, ChatMessage.Role.ASSISTANT, errorMsg, InputType.TEXT)
        return AgentResult.Error(AppError.ModelInferenceError(errorMsg))
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
