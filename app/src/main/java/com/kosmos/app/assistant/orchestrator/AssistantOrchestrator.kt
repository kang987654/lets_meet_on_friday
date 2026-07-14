package com.kosmos.app.assistant.orchestrator

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.context.PromptAssembler
import com.kosmos.app.assistant.context.ResponseParser
import com.kosmos.app.assistant.guard.ExecutionPolicy
import com.kosmos.app.assistant.guard.PreExecutionGuard
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.ModelOutput
import com.kosmos.app.domain.modelrunner.ModelRunner
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
 * - **Dependencies**: [ModelRunner], [ContextBuilder], [PromptAssembler], [PreExecutionGuard], Agents
 *
 * ### Key Flow
 * 1. 유저 메시지 DB 저장
 * 2. [ContextBuilder]를 통한 대화 기록 로드 (Token Sliding Window 적용)
 * 3. [PromptAssembler]로 최종 프롬프트 조립
 * 4. [ModelRunner] 실행 및 JSON 결과 파싱 (내부 루프를 통해 Tool 재귀 실행)
 * 5. Guard 정책 검사 후 결과 반환 또는 특정 Agent 로직(검색, 캘린더 등) 수행
 */
class AssistantOrchestrator @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val intentClassifier: IntentClassifier,
    private val contextBuilder: ContextBuilder,
    private val promptAssembler: PromptAssembler,
    private val modelRunner: ModelRunner,
    private val responseParser: ResponseParser,
    private val preExecutionGuard: PreExecutionGuard,
    private val auditTrailService: AuditTrailService,
    private val searchAgent: com.kosmos.app.assistant.agent.SearchAgent,
    private val calendarAgent: com.kosmos.app.assistant.agent.CalendarAgent,
    private val getTodayScheduleUseCase: com.kosmos.app.domain.usecase.GetTodayScheduleUseCase,
    private val addScheduleUseCase: com.kosmos.app.domain.usecase.AddScheduleUseCase
) {

    suspend fun processRequest(request: ChatRequest): AgentResult {
        // 1. 유저 메시지 저장
        val content = if (request.audioFilePath != null && request.message.isBlank()) "(음성 메시지)" else request.message
        val inputType = if (request.audioFilePath != null) InputType.VOICE else if (request.imageBytes != null) InputType.IMAGE else InputType.TEXT
        val saveUserResult = createAndSaveMessage(request.sessionId, ChatMessage.Role.USER, content, inputType)
        if (saveUserResult is AppResult.Failure) {
            return AgentResult.Error(saveUserResult.error)
        }

        // 2. 의도 분류 (힌트용)
        intentClassifier.classify(request.message)

        // 3. 문서 텍스트 저장
        if (request.documentText != null) {
            createAndSaveMessage(request.sessionId, ChatMessage.Role.SYSTEM, request.documentText, InputType.TEXT)
        }

        // 4. 컨텍스트 구성
        val contextResult = contextBuilder.build(request.sessionId)
        val context = when (contextResult) {
            is AppResult.Success -> contextResult.data
            is AppResult.Failure -> return handleErrorAndReturn(request.sessionId, "대화 문맥을 구성하지 못했습니다: ${contextResult.error}")
        }

        // 5. 프롬프트 조립
        var prompt = promptAssembler.assemble(context, request.message)

        // 6. 툴 루프 (Tool Loop) 실행
        var rawOutput = ""
        var parsedResult: com.kosmos.app.assistant.context.ToolParser.ParsedStream? = null

        coroutineScope {
            val scope = this
            while (true) {
                var toolCallDetected = false
                var accumulatedToken = ""
                val wrappedOnToken: (String) -> Unit = { token ->
                    accumulatedToken += token
                    val p = com.kosmos.app.assistant.context.ToolParser.parseStream(accumulatedToken)
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
                    is AppResult.Failure -> return@coroutineScope
                }

                parsedResult = com.kosmos.app.assistant.context.ToolParser.parseStream(rawOutput)

                if (parsedResult!!.toolCalls.isNotEmpty()) {
                    val call = parsedResult!!.toolCalls.first()
                    val toolJson = executeToolInner(call)
                    val newInput = "${prompt.currentInput}\n$rawOutput\n<tool_response>$toolJson</tool_response>"
                    prompt = prompt.copy(currentInput = newInput)
                    continue
                }
                break
            }
        }

        if (parsedResult == null) {
            return handleErrorAndReturn(request.sessionId, "Model execution failed")
        }

        auditTrailService.logModelRun(request.sessionId, prompt.currentInput, rawOutput)

        val modelOutput = responseParser.parse(parsedResult!!.content)

        // 7. 정책 가드 확인 및 반환
        return applyPolicyAndReturn(request.sessionId, modelOutput, parsedResult!!)
    }

    private suspend fun executeToolInner(call: com.kosmos.app.assistant.context.ToolParser.ToolCallData): String {
        return when (call.name) {
            "AddSchedule" -> {
                val title = call.args["title"] as? String ?: "Event"
                val startTime = call.args["startTime"] as? String ?: ""
                val endTime = call.args["endTime"] as? String ?: ""
                val desc = call.args["description"] as? String
                val res = addScheduleUseCase(title, startTime, endTime, desc)
                if (res is AppResult.Success) {
                    "{\"status\": \"success\", \"message\": \"일정이 성공적으로 추가되었습니다.\"}"
                } else {
                    "{\"status\": \"error\", \"message\": \"일정 추가 실패\"}"
                }
            }
            "GetSchedule" -> {
                val range = if ((call.args["date"] as? String)?.lowercase()?.contains("week") == true) {
                    com.kosmos.app.domain.model.ScheduleData.RangeType.WEEK
                } else {
                    com.kosmos.app.domain.model.ScheduleData.RangeType.TODAY
                }
                val res = getTodayScheduleUseCase(range)
                if (res is AppResult.Success) {
                    val data = res.data
                    val text = buildString {
                        append(data.summary ?: "일정 요약이 없습니다.")
                        append("\n\n")
                        data.events.forEach { event ->
                            append("- ${event.title} (${event.startIso})\n")
                        }
                    }
                    "{\"status\": \"success\", \"data\": \"$text\"}"
                } else {
                    "{\"status\": \"error\", \"message\": \"일정 조회 실패\"}"
                }
            }
            else -> "{\"status\": \"error\", \"message\": \"알 수 없는 Tool입니다.\"}"
        }
    }

    private suspend fun applyPolicyAndReturn(
        sessionId: String, 
        modelOutput: ModelOutput, 
        parsed: com.kosmos.app.assistant.context.ToolParser.ParsedStream
    ): AgentResult {
        return when (val policy = preExecutionGuard.checkPolicy(modelOutput)) {
            is ExecutionPolicy.Allowed -> {
                val text = if (modelOutput is ModelOutput.TextOutput) modelOutput.content else parsed.content
                createAndSaveMessage(sessionId, ChatMessage.Role.ASSISTANT, text, InputType.TEXT, searchUsed = false, thinkingProcess = parsed.thinking)
                AgentResult.Text(text, thinkingProcess = parsed.thinking)
            }
            is ExecutionPolicy.RequiresApproval -> {
                createAndSaveMessage(sessionId, ChatMessage.Role.ASSISTANT, parsed.content, InputType.TEXT, searchUsed = false, thinkingProcess = parsed.thinking)
                AgentResult.ActionRequired(modelOutput, thinkingProcess = parsed.thinking)
            }
            is ExecutionPolicy.Blocked -> {
                createAndSaveMessage(sessionId, ChatMessage.Role.ASSISTANT, policy.reason, InputType.TEXT)
                AgentResult.Text(policy.reason)
            }
        }
    }

    suspend fun resumeAction(sessionId: String, action: ModelOutput): AgentResult {
        return when (action) {
            is ModelOutput.SearchOutput -> handleSearchAction(sessionId, action)
            is ModelOutput.CalendarDraftOutput -> handleCalendarDraftAction(sessionId, action)
            is ModelOutput.GetScheduleOutput -> handleGetScheduleAction(sessionId, action)
            else -> AgentResult.Text("지원되지 않는 액션입니다.")
        }
    }

    private suspend fun handleSearchAction(sessionId: String, action: ModelOutput.SearchOutput): AgentResult {
        auditTrailService.logSearchEvent(sessionId, action.query)
        
        val searchResult = searchAgent.executeSearch(action.query)
        val searchContext = if (searchResult is AppResult.Success) searchResult.data else "웹 검색 네트워크 오류"
        
        createAndSaveMessage(sessionId, ChatMessage.Role.SYSTEM, searchContext, InputType.TEXT)

        val contextResult = contextBuilder.build(sessionId)
        val context = when (contextResult) {
            is AppResult.Success -> contextResult.data
            is AppResult.Failure -> return AgentResult.Error(contextResult.error)
        }

        val prompt = promptAssembler.assemble(context, "")
        val modelResult = modelRunner.generate(prompt)
        val rawOutput = when (modelResult) {
            is AppResult.Success -> modelResult.data
            is AppResult.Failure -> return handleErrorAndReturn(sessionId, "검색 후 응답 생성에 실패했습니다: ${modelResult.error}")
        }

        auditTrailService.logModelRun(sessionId, prompt.currentInput, rawOutput)
        
        val modelOutput = responseParser.parse(rawOutput)
        val text = if (modelOutput is ModelOutput.TextOutput) modelOutput.content else rawOutput
        
        createAndSaveMessage(sessionId, ChatMessage.Role.ASSISTANT, text, InputType.TEXT, searchUsed = true)
        return AgentResult.Text(text)
    }

    private suspend fun handleCalendarDraftAction(sessionId: String, action: ModelOutput.CalendarDraftOutput): AgentResult {
        val insertResult = calendarAgent.executeCalendarInsert(action)
        val text = when (insertResult) {
            is AppResult.Success -> "일정이 성공적으로 추가되었습니다."
            is AppResult.Failure -> "일정 추가에 실패했습니다: ${insertResult.error}"
        }
        createAndSaveMessage(sessionId, ChatMessage.Role.ASSISTANT, text, InputType.TEXT)
        return AgentResult.Text(text)
    }

    private suspend fun handleGetScheduleAction(sessionId: String, action: ModelOutput.GetScheduleOutput): AgentResult {
        val getResult = calendarAgent.executeGetSchedule(action, getTodayScheduleUseCase)
        val text = when (getResult) {
            is AppResult.Success -> getResult.data
            is AppResult.Failure -> "일정 조회에 실패했습니다: ${getResult.error}"
        }
        createAndSaveMessage(sessionId, ChatMessage.Role.ASSISTANT, text, InputType.TEXT)
        return AgentResult.Text(text)
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
