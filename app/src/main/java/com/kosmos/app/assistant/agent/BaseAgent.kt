package com.kosmos.app.assistant.agent

import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.context.ToolParser
import com.kosmos.app.assistant.orchestrator.ChatRequest
import com.kosmos.app.assistant.orchestrator.StreamUpdate
import com.kosmos.app.assistant.tool.ToolArguments
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
import com.kosmos.app.domain.modelrunner.ModelTurn
import com.kosmos.app.domain.modelrunner.ToolResponseInput
import kotlinx.coroutines.coroutineScope
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
        // [WHY] enabledTools 를 프롬프트에 실어야 런타임이 모델에게 툴을 선언한다 (ADR-008).
        var prompt = initialPrompt.copy(enabledTools = allowedTools)
        // [WHY] 자리표 `null` 을 두지 않는다. 루프를 빠져나오는 유일한 경로(`break`)는 두 값을
        // 대입한 뒤에만 나오므로 초기값이 필요 없다. 예전에는 `null` 로 시작한 탓에 루프 뒤에
        // 절대 발동하지 않는 방어 코드가 붙어 있었고, 조기 탈출이 새로 생겨도 그 죽은 분기가
        // 삼켜 사용자에게 엉뚱한 오류를 띄웠을 것이다. 지금은 같은 상황이 컴파일 에러로 드러난다.
        var lastTurn: ModelTurn
        var parsedResult: ToolParser.ParsedStream
        var isFirstTurn = true
        var loopCount = 0
        // [WHY] 웹 검색이 실제로 실행됐는지는 이 루프만 안다. 예전에는 최종 메시지를 항상
        // `searchUsed = false` 로 저장해, DB 컬럼과 화면 표시가 통째로 죽어 있었다.
        var searchUsed = false
        // [WHY] 검색을 시도했는데 실패한 경우다. 사용자에게 "이 답은 기기 안에서만 만든 것"임을
        // 알려야 검색 결과로 오해하지 않는다 (PRD V1-AC3·EC5).
        var searchFailed = false
        val MAX_TOOL_LOOP_COUNT = 3
        val THINK_TAG_WINDOW = 12 // "<|think|" 태그가 토큰 경계에 걸려도 감지되는 길이

        while (true) {
            if (loopCount >= MAX_TOOL_LOOP_COUNT) {
                auditTrailService.logError(request.sessionId, "Max tool loop count exceeded")
                // [WHY] 루프 상한 초과는 타임아웃이 아니므로 오해를 부르는 Timeout 대신 추론 오류로 보고한다.
                return@coroutineScope AgentResult.Error(AppError.ModelInferenceError("툴 호출 반복 상한(${MAX_TOOL_LOOP_COUNT}회)을 초과했습니다."))
            }
            loopCount++

            var tagSeen = false
            var tailWindow = ""
            var accumulatedToken = ""
            // [WHY] 스트리밍 파싱이 여기 한 곳에만 있다. 이 누적기는 루프 안에 선언돼 턴마다
            // 리셋되므로, UI 가 직접 누적하던 시절의 "1턴 문장이 2턴에 이어붙는" 결함이
            // 구조적으로 불가능해진다 (ADR-007).
            //
            // [WHY] 툴 호출 감지는 더 이상 텍스트 파싱이 아니다 — 런타임이 구조화된 호출을
            // 돌려주므로 `<tool_call` 윈도우와 조기 취소가 사라졌다. `<|think|>` 처리는 남긴다
            // (모델이 생각 텍스트를 낼 수 있고, 방어 비용이 싸다).
            val wrappedOnToken: (String) -> Unit = { token ->
                accumulatedToken += token
                // [WHY] 토큰마다 전체 누적 문자열을 정규식 재파싱하면 O(n²) 핫패스가 된다.
                // 태그가 하나도 없는 구간에서는 누적 문자열이 곧 본문이므로 파싱이 필요 없다.
                val probe = tailWindow + token
                if (!tagSeen && probe.contains("<|think|")) {
                    tagSeen = true
                }
                tailWindow = accumulatedToken.takeLast(THINK_TAG_WINDOW)

                val update = if (tagSeen) {
                    val p = ToolParser.parseStream(accumulatedToken)
                    StreamUpdate(p.content.ifEmpty { null }, p.thinking)
                } else {
                    // [WHY] 태그가 완성되기 전 꼬리 조각(`<|th`)도 잘라야 한다 — 감지 윈도우는
                    // 완전한 태그만 찾으므로 그 전 구간을 그대로 흘리면 조각이 보인다.
                    StreamUpdate(ToolParser.stripIncompleteTag(accumulatedToken).ifEmpty { null }, null)
                }
                request.onStream?.invoke(update)
            }

            // [WHY] 첫 턴에만 첨부(이미지)를 보낸다. 이후는 툴 응답 회신 턴이다.
            //
            // [WHY] 오디오 분기는 없다 — 음성은 `AssistantOrchestrator` 가 먼저 전사해
            // 텍스트로 바꿔 보내므로 에이전트까지 오디오가 오지 않는다 (ADR-014).
            val modelResult = if (request.imageBytes != null && isFirstTurn) {
                modelRunner.generateWithImage(prompt, request.imageBytes, wrappedOnToken)
            } else {
                modelRunner.generate(prompt, wrappedOnToken)
            }
            isFirstTurn = false

            val turn = when (modelResult) {
                is AppResult.Success -> modelResult.data
                is AppResult.Failure -> return@coroutineScope handleErrorAndReturn(
                    request.sessionId,
                    "Model inference failed: ${modelResult.error}",
                    // [WHY] 원인을 그대로 넘겨야 발열·모델 미준비 같은 구체적 안내가 나온다.
                    modelResult.error
                )
            }
            lastTurn = turn
            parsedResult = ToolParser.parseStream(turn.text)

            if (turn.toolCalls.isNotEmpty()) {
                val call = turn.toolCalls.first() // 병렬 처리 방지
                val args = ToolArguments.of(call.args)
                val outcome = executeToolInner(
                    ToolParser.ToolCallData(call.name, args),
                    request.sessionId,
                    allowedTools
                )
                // [WHY] 감사 기록을 여기서 남긴다 — 이 지점만이 "어떤 툴이 실제로 실행되고
                // 무엇을 돌려줬는가"를 안다. 승인 기록만으로는 승인이 필요 없는 툴(일정 조회,
                // 웹 검색)의 실행이 감사에서 통째로 빠진다.
                //
                // [WHY] 추가 호출을 무음으로 버리지 않는다. 첫 호출만 실행하는 것은 의도한
                // 정책이지만(병렬 실행 방지), 버려진 사실이 어디에도 남지 않으면 "왜 두 번째
                // 일이 안 됐는가"를 나중에 추적할 방법이 없다.
                val dropped = turn.toolCalls.drop(1)
                auditTrailService.logToolCall(
                    sessionId = request.sessionId,
                    toolName = call.name,
                    resultJson = outcome.resultJson,
                    note = if (dropped.isEmpty()) null
                    else "같은 턴의 추가 호출 ${dropped.size}건 미실행: ${dropped.map { it.name }}"
                )
                // [WHY] 웹 검색은 유일한 네트워크 egress 다. `SEARCH_USED` 감사 타입과
                // 포맷터가 이미 있었지만 호출하는 곳이 없어, 프라이버시상 가장 기록이 필요한
                // 동작이 감사 로그에 남지 않았다.
                if (call.name == "SearchWikipedia") {
                    if (outcome.executed) {
                        searchUsed = true
                        auditTrailService.logSearchEvent(
                            request.sessionId,
                            args.optString("topic") ?: ""
                        )
                    } else if (call.name in allowedTools) {
                        // [WHY] allowlist 밖(토글 OFF)이면 '실패'가 아니라 '허용하지 않음'이다.
                        // 그 경우까지 "웹 검색 보강 실패"를 띄우면 사용자가 스스로 끈 것을
                        // 오류로 오해한다. 허용됐는데 실패한 경우만 알린다.
                        searchFailed = true
                    }
                }
                // [WHY] Conversation은 stateful이라 직전 입력/출력이 이미 컨텍스트에 있다.
                // 툴 결과만 전용 응답 타입으로 되돌린다 — 이전에는 `<tool_response>` 텍스트를
                // 사용자 턴으로 위장해 보냈다.
                prompt = prompt.copy(
                    currentInput = "",
                    toolResponse = ToolResponseInput(call.name, outcome.resultJson)
                )
                continue
            }
            break
        }

        val finalParsed = parsedResult

        // [WHY] `prompt.currentInput` 이 아니라 원문 요청을 기록한다. 툴을 쓴 턴에서는 루프가
        // `currentInput = ""` 로 덮어쓰므로, 예전에는 **툴을 쓴 대화일수록** 감사 로그의
        // 프롬프트가 빈 문자열이었다 — 정작 기록이 가장 필요한 턴이 비어 있었다.
        auditTrailService.logModelRun(
            request.sessionId,
            request.message,
            lastTurn.text,
            declaredTools = allowedTools
        )

        // [WHY] 일정 초안 등 액션성 흐름은 모두 툴 콜 + 승인 경로로 일원화되었으므로(2026-07-31 절충안),
        // 최종 응답은 텍스트로 저장·반환한다. (구 ResponseParser/PreExecutionGuard 경로 제거)
        val text = finalParsed.content
        createAndSaveMessage(request.sessionId, ChatMessage.Role.ASSISTANT, text, InputType.TEXT, searchUsed = searchUsed, thinkingProcess = finalParsed.thinking)
        return@coroutineScope AgentResult.Text(
            text,
            thinkingProcess = finalParsed.thinking,
            searchUsed = searchUsed,
            searchFailed = searchFailed
        )
    }

    /**
     * 툴 실행 결과입니다.
     *
     * [WHY] 예전에는 JSON 문자열만 돌려줬다. 그러면 호출자가 "실제로 실행됐는가"를 알려면
     * 그 문자열에서 `"status":"error"` 를 찾아야 하는데, 오류 JSON 을 만드는 곳이 네 군데이고
     * 그중 하나는 공백이 다른 손수 만든 리터럴이라(`{"status": "error"...}`) 문자열 검사가
     * 조용히 빗나간다. 실행 여부는 값으로 들고 나온다.
     */
    private data class ToolOutcome(val resultJson: String, val executed: Boolean)

    private suspend fun executeToolInner(
        call: ToolParser.ToolCallData,
        sessionId: String,
        allowedTools: List<String>
    ): ToolOutcome {
        // [WHY] allowlist가 프롬프트 텍스트에만 있으면 모델(또는 주입된 문서)이 임의 툴을 호출해도
        // 실행되므로, 실행 시점에 반드시 재검증한다.
        if (call.name !in allowedTools) {
            auditTrailService.logError(sessionId, "Blocked tool call outside allowlist: ${call.name}")
            val message = if (call.name == "SearchWikipedia") {
                "웹 검색이 비활성화되어 있습니다. 사용자에게 채팅 화면 상단의 웹 검색 토글을 켜도록 안내하세요."
            } else {
                "이 에이전트에서 사용할 수 없는 도구입니다: ${call.name}"
            }
            return ToolOutcome(
                org.json.JSONObject().put("status", "error").put("message", message).toString(),
                executed = false
            )
        }

        val executor = toolRegistry.getExecutor(call.name)
            ?: return ToolOutcome(
                org.json.JSONObject().put("status", "error")
                    .put("message", "알 수 없는 Tool입니다: ${call.name}").toString(),
                executed = false
            )

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
                    return ToolOutcome(
                        org.json.JSONObject().put("status", "error")
                            .put("message", "사용자가 취소했습니다").toString(),
                        executed = false
                    )
                }
                auditTrailService.logApprovalGranted(sessionId, "${call.name}: ${approvalRequest.description}")
            }
            ToolOutcome(executor.execute(call.args, sessionId), executed = true)
        } catch (e: com.kosmos.app.assistant.tool.ToolArgumentException) {
            auditTrailService.logError(sessionId, "Invalid tool argument: ${call.name}.${e.field} (${e.reason})")
            ToolOutcome(toolArgumentErrorJson(call.name, e), executed = false)
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

    /**
     * @param error 사용자에게 보여줄 오류. null 이면 일반 추론 오류로 취급합니다.
     *
     * [WHY] 말풍선에 저장되는 문구를 `ErrorMessages` 로 인간화한다. 예전에는
     * `"Model inference failed: TemperatureCritical(48.3)"` 같은 내부 문자열이 그대로 대화
     * 기록에 남았다(스낵바만 인간화돼 있었다). 감사 로그에는 원문을 남겨 진단은 유지한다.
     */
    private suspend fun handleErrorAndReturn(
        sessionId: String,
        errorMsg: String,
        error: AppError? = null
    ): AgentResult.Error {
        auditTrailService.logError(sessionId, errorMsg)
        val shown = error ?: AppError.ModelInferenceError(errorMsg)
        createAndSaveMessage(
            sessionId,
            ChatMessage.Role.ASSISTANT,
            com.kosmos.app.core.mapper.ErrorMessages.userMessage(shown),
            InputType.TEXT
        )
        return AgentResult.Error(shown)
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
