package com.kosmos.app.assistant.agent

import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.context.ToolParser
import com.kosmos.app.assistant.orchestrator.ChatRequest
import com.kosmos.app.assistant.orchestrator.StreamUpdate
import com.kosmos.app.assistant.tool.ToolRegistry
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
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
 * - **Dependencies**: [ModelRunner], [ToolRegistry], [ApprovalCoordinator]
 *
 * ### Key Flow
 * 1. 하위 클래스에서 정의된 Prompt를 기반으로 추론을 시작합니다.
 * 2. Tool Call이 발생하면 파싱하여 도구를 실행하고, 그 결과를 컨텍스트에 추가하여 재귀적으로 추론합니다.
 * 3. 최종적으로 도출된 텍스트 응답을 [AgentResult]로 감싸서 반환합니다.
 */
abstract class BaseAgent(
    protected val modelRunner: ModelRunner,
    protected val toolRegistry: ToolRegistry,
    protected val auditTrailService: AuditTrailService,
    protected val conversationRepository: ConversationRepository,
    protected val approvalCoordinator: ApprovalCoordinator
) {
    abstract suspend fun execute(request: ChatRequest, context: ContextBuilder.Context): AgentResult

    /** 이 에이전트가 현재 컨텍스트에서 사용할 수 있는 툴 이름 목록 (프롬프트 노출 + 실행 시점 강제에 공용). */
    protected abstract fun availableTools(context: ContextBuilder.Context): List<String>

    protected suspend fun executeToolLoop(
        request: ChatRequest,
        initialPrompt: ChatPrompt,
        allowedTools: List<String>
    ): AgentResult = coroutineScope {
        var prompt = initialPrompt
        var rawOutput = ""
        var parsedResult: ToolParser.ParsedStream? = null
        var loopCount = 0
        val MAX_TOOL_LOOP_COUNT = 3
        val TOOL_TAG_WINDOW = 12 // "<tool_call"/"<|think|" 태그가 토큰 경계에 걸려도 감지되는 길이

        val scope = this
        var cancelJob: kotlinx.coroutines.Job? = null
        while (true) {
            if (loopCount >= MAX_TOOL_LOOP_COUNT) {
                auditTrailService.logError(request.sessionId, "Max tool loop count exceeded")
                // [WHY] 루프 상한 초과는 타임아웃이 아니므로 오해를 부르는 Timeout 대신 추론 오류로 보고한다.
                return@coroutineScope AgentResult.Error(AppError.ModelInferenceError("툴 호출 반복 상한(${MAX_TOOL_LOOP_COUNT}회)을 초과했습니다."))
            }
            loopCount++

            var toolCallDetected = false
            var tagSeen = false
            var tailWindow = ""
            var accumulatedToken = ""
            // [WHY] 스트리밍 파싱이 여기 한 곳에만 있다. 이 누적기는 루프 안에 선언돼 턴마다
            // 리셋되므로, UI 가 직접 누적하던 시절의 "1턴 문장이 2턴에 이어붙는" 결함이
            // 구조적으로 불가능해진다 (ADR-007).
            val wrappedOnToken: (String) -> Unit = { token ->
                accumulatedToken += token
                // [WHY] 토큰마다 전체 누적 문자열을 정규식 재파싱하면 O(n²) 핫패스가 된다.
                // 경계 윈도우로 태그 시작을 감지한 뒤에만 파싱을 수행한다. 태그가 하나도
                // 없는 구간에서는 누적 문자열이 곧 본문이므로 파싱이 아예 필요 없다.
                val probe = tailWindow + token
                if (!tagSeen && (probe.contains("<tool_call") || probe.contains("<|think|"))) {
                    tagSeen = true
                }
                tailWindow = accumulatedToken.takeLast(TOOL_TAG_WINDOW)

                val update = if (tagSeen) {
                    val p = ToolParser.parseStream(accumulatedToken)
                    if (!toolCallDetected && p.toolCalls.isNotEmpty()) {
                        toolCallDetected = true
                        cancelJob = scope.launch { modelRunner.cancel() }
                    }
                    StreamUpdate(p.content.ifEmpty { null }, p.thinking)
                } else {
                    // [WHY] 태그가 아직 완성되지 않았어도 꼬리에 걸린 조각(`<tool_`)은 잘라야
                    // 한다. 감지 윈도우는 완전한 태그 문자열만 찾으므로, 그 전 구간을 그대로
                    // 흘리면 조각이 한 글자씩 자라는 것이 보인다. 정규식 파싱 없이 꼬리만 본다.
                    StreamUpdate(ToolParser.stripIncompleteTag(accumulatedToken).ifEmpty { null }, null)
                }
                request.onStream?.invoke(update)
            }

            val modelResult = if (request.audioFilePath != null && rawOutput.isEmpty()) {
                modelRunner.generateWithAudio(prompt, request.audioFilePath, wrappedOnToken)
            } else if (request.imageBytes != null && rawOutput.isEmpty()) {
                modelRunner.generateWithImage(prompt, request.imageBytes, request.imageTokenBudget, wrappedOnToken)
            } else {
                modelRunner.generate(prompt, wrappedOnToken)
            }

            // [WHY] 중간 취소(cancelProcess)가 fire-and-forget으로 남아 있으면 다음 루프의
            // 추론을 죽일 수 있으므로, 이번 생성 종료 후 반드시 완료를 기다린다.
            cancelJob?.join()
            cancelJob = null

            rawOutput = when (modelResult) {
                is AppResult.Success -> modelResult.data
                is AppResult.Failure -> return@coroutineScope handleErrorAndReturn(request.sessionId, "Model inference failed: ${modelResult.error.toString()}")
            }

            val parsed = ToolParser.parseStream(rawOutput)
            parsedResult = parsed

            if (parsed.toolCalls.isNotEmpty()) {
                val call = parsed.toolCalls.first() // 병렬 처리 방지
                val toolJson = executeToolInner(call, request.sessionId, allowedTools)
                // [WHY] Conversation은 stateful이라 직전 입력/출력이 이미 컨텍스트에 있다.
                // currentInput+rawOutput을 재전송하면 매 반복마다 내용이 중복 누적되므로 tool_response만 보낸다.
                val newInput = "<tool_response>\n$toolJson\n</tool_response>"
                prompt = prompt.copy(currentInput = newInput)
                continue
            }

            // [WHY] JSON이 깨진 tool_call은 이전에 조용히 버려져, 모델은 오류를 받지 못하고
            // 그 턴이 평문 답변으로 처리됐다 — 사용자에게는 요청이 무시된 것처럼 보인다.
            // 형식 오류를 되돌려 모델이 스스로 고쳐 다시 호출할 기회를 준다.
            if (parsed.malformedToolCalls.isNotEmpty()) {
                auditTrailService.logError(
                    request.sessionId,
                    "Malformed tool_call JSON: ${parsed.malformedToolCalls.first().take(200)}"
                )
                val errorJson = org.json.JSONObject()
                    .put("status", "error")
                    .put(
                        "message",
                        "tool_call 의 JSON 형식이 올바르지 않습니다. " +
                            "{\"name\": \"툴이름\", \"args\": { ... }} 형태로 다시 작성하세요."
                    )
                    .toString()
                prompt = prompt.copy(currentInput = "<tool_response>\n$errorJson\n</tool_response>")
                continue
            }
            break
        }

        val finalParsed = parsedResult
            ?: return@coroutineScope handleErrorAndReturn(request.sessionId, "Model execution failed")

        auditTrailService.logModelRun(request.sessionId, prompt.currentInput, rawOutput)

        // [WHY] 일정 초안 등 액션성 흐름은 모두 툴 콜 + 승인 경로로 일원화되었으므로(2026-07-31 절충안),
        // 최종 응답은 텍스트로 저장·반환한다. (구 ResponseParser/PreExecutionGuard 경로 제거)
        val text = finalParsed.content
        createAndSaveMessage(request.sessionId, ChatMessage.Role.ASSISTANT, text, InputType.TEXT, searchUsed = false, thinkingProcess = finalParsed.thinking)
        return@coroutineScope AgentResult.Text(text, thinkingProcess = finalParsed.thinking)
    }

    private suspend fun executeToolInner(
        call: ToolParser.ToolCallData,
        sessionId: String,
        allowedTools: List<String>
    ): String {
        // [WHY] allowlist가 프롬프트 텍스트에만 있으면 모델(또는 주입된 문서)이 임의 툴을 호출해도
        // 실행되므로, 실행 시점에 반드시 재검증한다.
        if (call.name !in allowedTools) {
            auditTrailService.logError(sessionId, "Blocked tool call outside allowlist: ${call.name}")
            val message = if (call.name == "SearchWikipedia") {
                "웹 검색이 비활성화되어 있습니다. 사용자에게 채팅 화면 상단의 웹 검색 토글을 켜도록 안내하세요."
            } else {
                "이 에이전트에서 사용할 수 없는 도구입니다: ${call.name}"
            }
            return org.json.JSONObject().put("status", "error").put("message", message).toString()
        }

        val executor = toolRegistry.getExecutor(call.name)
            ?: return org.json.JSONObject().put("status", "error")
                .put("message", "알 수 없는 Tool입니다: ${call.name}").toString()

        // [WHY] 인자 검증 실패는 승인 전에 걸러야 한다 — 필수 인자가 없는 초안을 승인 카드로
        // 띄우면 사용자가 실행될 수 없는 요청을 승인하게 된다. 또한 누락과 타입 오류를 구분해
        // 되돌려야 모델이 "안 보냈다"와 "모양이 틀렸다" 중 무엇을 고칠지 알 수 있다.
        return try {
            val actionType = executor.actionType
            if (actionType != null && com.kosmos.app.core.security.ApprovalRules.requiresApproval(actionType)) {
                val approvalRequest = executor.buildApprovalRequest(call.args, sessionId)
                val approved = approvalCoordinator.requireApproval(approvalRequest)
                if (!approved) {
                    auditTrailService.logApprovalRejected(sessionId, "${call.name}: ${approvalRequest.description}")
                    return "{\"status\": \"error\", \"message\": \"사용자가 취소했습니다\"}"
                }
                auditTrailService.logApprovalGranted(sessionId, "${call.name}: ${approvalRequest.description}")
            }
            executor.execute(call.args, sessionId)
        } catch (e: com.kosmos.app.assistant.tool.ToolArgumentException) {
            auditTrailService.logError(sessionId, "Invalid tool argument: ${call.name}.${e.field} (${e.reason})")
            toolArgumentErrorJson(call.name, e)
        }
    }

    /** 인자 오류를 모델이 스스로 고칠 수 있는 구조화된 형태로 되돌립니다. */
    private fun toolArgumentErrorJson(
        toolName: String,
        e: com.kosmos.app.assistant.tool.ToolArgumentException
    ): String {
        val guidance = when (e.reason) {
            com.kosmos.app.assistant.tool.ToolArgumentException.Reason.MISSING ->
                "'${e.field}' 인자가 없습니다. 사용자에게 값을 확인한 뒤 다시 호출하세요."
            com.kosmos.app.assistant.tool.ToolArgumentException.Reason.WRONG_TYPE ->
                "'${e.field}' 인자의 형식이 올바르지 않습니다. 문자열로 다시 보내세요."
        }
        return org.json.JSONObject()
            .put("status", "error")
            .put("tool", toolName)
            .put("field", e.field)
            .put("reason", e.reason.name.lowercase())
            .put("message", guidance)
            .toString()
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
