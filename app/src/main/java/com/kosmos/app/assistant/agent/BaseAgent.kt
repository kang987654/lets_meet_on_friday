package com.kosmos.app.assistant.agent

import com.kosmos.app.domain.tool.ToolNames
import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.context.ToolParser
import com.kosmos.app.assistant.orchestrator.AssistantMessageWriter
import com.kosmos.app.assistant.orchestrator.ChatRequest
import com.kosmos.app.assistant.orchestrator.StreamUpdate
import com.kosmos.app.assistant.tool.ToolArguments
import com.kosmos.app.assistant.tool.ToolRegistry
import com.kosmos.app.assistant.tool.ToolResultJson
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
    private val messageWriter = AssistantMessageWriter(conversationRepository, auditTrailService)

    // [WHY] 사실 저장 가드가 막은 메모 — 다음 턴이 저장 물음에 "응"으로 답하면 모델이 쓴 이 문장을 승인 시트에 그대로 쓴다(0.38.9 M1).
    @Volatile private var blockedMemo: Pair<String, String>? = null

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
        // [WHY] SearchMemory 가 참조한 에피소드 출처 — 회수 칩(🧠)의 데이터 (ADR-022).
        val recallEpisodeIds = mutableListOf<String>()
        var messageResult: com.kosmos.app.domain.model.MessageResult? = null
        // 이 턴에서 실행된 툴이 있는가, 가드가 막은 것 말고 메모 저장 호출이 있었는가 — 답 고치기·앱이 여는 저장 시트의 조건.
        var anyExecuted = false
        var memoCalled = false
        val carriedMemo = blockedMemo?.takeIf { it.first == request.sessionId }?.second
        blockedMemo = null
        var previous: com.kosmos.app.domain.memory.MemoSaveIntent.PreviousTurn? = null
        suspend fun previousTurn() = previous ?: loadPreviousTurn(request.sessionId).also { previous = it }
        while (true) {
            if (loopCount >= MAX_TOOL_LOOP_COUNT) {
                // [WHY] 루프 상한 초과는 타임아웃이 아니므로 오해를 부르는 Timeout 대신 추론 오류로 보고한다.
                //
                // [WHY] handleErrorAndReturn 을 경유해 말풍선도 남긴다. 예전에는 Error 만 직반환해
                // 화면에 아무 흔적이 없었다. 시각 인자 형식 검증(BAD_FORMAT)이 생기면서 모델이
                // 같은 깨진 값으로 상한까지 재시도하는 경로가 현실이 됐고(greedy 는 재시도가
                // 동일할 수 있다), 그 끝이 무반응이면 사용자는 앱이 죽었다고 오해한다.
                return@coroutineScope messageWriter.saveError(
                    request.sessionId,
                    "Max tool loop count exceeded",
                    AppError.ModelInferenceError("툴 호출 반복 상한(${MAX_TOOL_LOOP_COUNT}회)을 넘었어요.")
                )
            }
            loopCount++

            var tagSeen = false
            var tailWindow = ""
            // [WHY] StringBuilder — 토큰마다 String += 로 누적하면 긴 응답에서 O(n²) 복사가 된다.
            val accumulated = StringBuilder()
            // [WHY] 스트리밍 파싱이 여기 한 곳에만 있다. 이 누적기는 루프 안에 선언돼 턴마다
            // 리셋되므로, UI 가 직접 누적하던 시절의 "1턴 문장이 2턴에 이어붙는" 결함이
            // 구조적으로 불가능해진다 (ADR-007).
            //
            // [WHY] 툴 호출 감지는 더 이상 텍스트 파싱이 아니다 — 런타임이 구조화된 호출을
            // 돌려주므로 `<tool_call` 윈도우와 조기 취소가 사라졌다. `<|think|>` 처리는 남긴다
            // (모델이 생각 텍스트를 낼 수 있고, 방어 비용이 싸다).
            val wrappedOnToken: (String) -> Unit = { token ->
                accumulated.append(token)
                val accumulatedToken = accumulated.toString()
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
                is AppResult.Failure -> return@coroutineScope messageWriter.saveError(
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
                    allowedTools,
                    request.message,
                    previousAssistant = if (call.name == ToolNames.ADD_MEMORY) previousTurn().assistant else null
                )
                anyExecuted = anyExecuted || outcome.executed
                if (call.name == ToolNames.ADD_MEMORY) {
                    if (outcome.factBlocked) {
                        args.optString("content")?.takeIf { it.isNotBlank() }?.let { blockedMemo = request.sessionId to it }
                    } else {
                        memoCalled = true
                    }
                }
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
                if (call.name == ToolNames.SEARCH_WIKIPEDIA) {
                    // [WHY] 뱃지 기준은 executed 가 아니라 **succeeded** 다. 위키 네트워크 실패는
                    // 예외가 아니라 오류 JSON 으로 돌아오므로(executed=true), 예전 기준으로는
                    // 실패한 검색에도 "참고했어요" 뱃지가 붙었다. 감사 기록은 executed 기준을
                    // 유지한다 — 네트워크 시도 자체가 프라이버시상 기록할 사건이다.
                    if (outcome.executed) {
                        auditTrailService.logSearchEvent(
                            request.sessionId,
                            args.optString("topic") ?: ""
                        )
                    }
                    if (outcome.succeeded) {
                        searchUsed = true
                    } else if (call.name in allowedTools) {
                        // [WHY] allowlist 밖(토글 OFF)이면 '실패'가 아니라 '허용하지 않음'이다.
                        // 그 경우까지 "웹 검색 보강 실패"를 띄우면 사용자가 스스로 끈 것을
                        // 오류로 오해한다. 허용됐는데 실패한 경우만 알린다.
                        searchFailed = true
                    }
                }
                // [WHY] SearchMemory 의 meta.episodeIds 는 회수 칩의 출처다 — 여기서 뽑아 두고,
                // **모델에 되돌리기 전에 meta 를 제거**한다. 남겨 보내면 프롬프트 토큰을 낭비하고
                // 모델이 id 를 답변에 에코하거나 형식을 모방할 재료가 된다 (ADR-022).
                if (call.name == ToolNames.SEARCH_MEMORY && outcome.succeeded) {
                    recallEpisodeIds += extractEpisodeIds(outcome.resultJson)
                }
                // [WHY] 결과 카드(0.38.2)의 대상 — 마지막으로 성공한 툴의 것. meta 와 함께 모델 입력에서 떼어 낸다.
                if (outcome.succeeded) extractResult(outcome.resultJson)?.let { messageResult = it }

                // [WHY] 승인 거절은 모델을 다시 부르지 않고 고정 문장으로 턴을 끝낸다(0.38.6 M4 사용자 결정) — 모델에게 거절을 돌려주면
                // 바로 앞 턴의 답을 따라 하거나("일정을 추가해도 될까요?"), 지시문을 읊거나("저장하지 않았다고 말씀드릴게요"), 거절을 오류로
                // 말했다(실기기 10-23, exp56). 살아 있는 대화에는 회신 없는 툴 호출이 남으므로 다음 사용자 턴에 DB 히스토리로 다시 만든다.
                if (outcome.rejected) {
                    anyExecuted = true
                    modelRunner.invalidateConversation()
                    val reply = rejectionReply(call.name)
                    request.onStream?.invoke(StreamUpdate(reply, null))
                    parsedResult = ToolParser.parseStream(reply)
                    break
                }

                // [WHY] Conversation은 stateful이라 직전 입력/출력이 이미 컨텍스트에 있다.
                // 툴 결과만 전용 응답 타입으로 되돌린다 — 이전에는 `<tool_response>` 텍스트를
                // 사용자 턴으로 위장해 보냈다.
                prompt = prompt.copy(
                    currentInput = "",
                    toolResponse = ToolResponseInput(call.name, stripMeta(outcome.resultJson))
                )
                continue
            }
            break
        }

        val finalParsed = parsedResult
        var text = finalParsed.content

        // [WHY] 툴을 하나도 실행하지 않은 턴만 고친다 — 저장 물음에 "응"이라 했는데 모델이 저장을 부르지 않고 "메모해 두었어요"라고만 하면
        // 앱이 저장 시트를 직접 연다(exp64b 2/6, 0.38.9 사용자 결정). 그 밖에는 지켜지지 않을 "기억해 둘게요"를 물음으로 바꾼다.
        // 고친 답은 살아 있는 대화(KV)의 답과 다르므로 다음 사용자 턴에 DB 히스토리로 다시 만든다.
        if (!anyExecuted && !memoCalled) {
            val intent = com.kosmos.app.domain.memory.MemoSaveIntent
            val askedBefore = intent.isYes(request.message) && intent.asksSave(previousTurn().assistant)
            val memo = if (askedBefore) carriedMemo ?: previousTurn().user else null
            val fixed = if (memo != null && ToolNames.ADD_MEMORY in allowedTools) {
                saveAnsweredMemo(request.sessionId, memo, allowedTools)?.let { (reply, result) ->
                    if (result != null) messageResult = result
                    reply
                }
            } else {
                null
            } ?: intent.withoutClaim(text)
            if (fixed != text) {
                text = fixed
                modelRunner.invalidateConversation()
                request.onStream?.invoke(StreamUpdate(text, null))
            }
        }

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
        // [WHY] 저장 결과를 버리지 않는다. 실패해도 응답은 이미 화면에 있으므로 턴을 실패로
        // 바꾸지 않되, 재시작 후 이 턴이 사라진다는 사실을 사용자가 알아야 한다(persistFailed).
        // 감사 로그에는 원인을 남긴다.
        val recalled = recallEpisodeIds.distinct()
        val saveResult = messageWriter.save(
            request.sessionId, ChatMessage.Role.ASSISTANT, text, InputType.TEXT,
            searchUsed = searchUsed,
            thinkingProcess = finalParsed.thinking,
            episodeId = request.episodeId,
            recallEpisodeIds = recalled,
            result = messageResult
        )
        if (saveResult is AppResult.Failure) {
            auditTrailService.logError(request.sessionId, "비서 응답 저장 실패: ${saveResult.error}")
        }
        return@coroutineScope AgentResult.Text(
            text,
            thinkingProcess = finalParsed.thinking,
            searchUsed = searchUsed,
            searchFailed = searchFailed,
            persistFailed = saveResult is AppResult.Failure,
            recallEpisodeIds = recalled,
            result = messageResult
        )
    }

    /**
     * 툴 실행 결과입니다.
     *
     * [WHY] 예전에는 JSON 문자열만 돌려줬다. 그러면 호출자가 "실제로 실행됐는가"를 알려면
     * 그 문자열에서 `"status":"error"` 를 찾아야 하는데, 오류 JSON 을 만드는 곳이 네 군데이고
     * 그중 하나는 공백이 다른 손수 만든 리터럴이라(`{"status": "error"...}`) 문자열 검사가
     * 조용히 빗나간다. 실행 여부는 값으로 들고 나온다.
     *
     * [WHY] `succeeded` 는 `executed` 와 다르다. executor 가 실패를 예외가 아니라
     * `{"status":"error"}` JSON 으로 돌려주면(위키 네트워크 실패가 그렇다) executed=true 인데,
     * 예전에는 그것만 보고 searchUsed 를 켜서 **검색이 실패한 답변에 "위키백과 검색 결과를
     * 참고했어요" 뱃지가 붙었다** — 검색 없이 만든 답을 근거 있는 답으로 오표시하는, PRD
     * V1-AC3·EC5 를 실질 무력화하는 결함이었다. 판정은 문자열 검색이 아니라 JSON 파싱이다 —
     * executor 출력은 전부 JSONObject 로 만들어져 형태가 균일하다.
     */
    private data class ToolOutcome(
        val resultJson: String,
        val executed: Boolean,
        val succeeded: Boolean = executed,
        // 사용자가 승인하지 않음(시간 초과 포함) — 루프가 고정 문장으로 끝낸다.
        val rejected: Boolean = false,
        // 사실 저장 가드가 막음 — 모델은 막힘 결과를 받고 먼저 묻는다.
        val factBlocked: Boolean = false
    ) {
        companion object {
            fun of(resultJson: String, executed: Boolean): ToolOutcome {
                val succeeded = executed && !ToolResultJson.isError(resultJson)
                return ToolOutcome(resultJson, executed, succeeded)
            }
        }
    }

    /** 지금 턴 바로 앞의 비서 말·사용자 말. 읽기 실패는 "앞 턴 없음" — 가드가 막는 쪽으로 기울 뿐이다. */
    private suspend fun loadPreviousTurn(sessionId: String): com.kosmos.app.domain.memory.MemoSaveIntent.PreviousTurn {
        val recent = com.kosmos.app.core.common.runCatchingCancellable {
            (conversationRepository.getRecentBySession(sessionId, PREVIOUS_TURN_SCAN) as? AppResult.Success)?.data
        }.getOrNull().orEmpty()
        return com.kosmos.app.domain.memory.MemoSaveIntent.previousTurn(recent)
    }

    /**
     * 저장 물음에 "응"이라 답했는데 모델이 저장을 부르지 않은 턴 — 앱이 메모 저장(승인 시트)을 직접 실행하고 결과에 맞는 고정 문장을 돌려준다.
     * 실행 경로에 들지 못하면(인자 오류 등) null — 모델 답을 그대로 둔다.
     */
    private suspend fun saveAnsweredMemo(
        sessionId: String,
        content: String,
        allowedTools: List<String>
    ): Pair<String, com.kosmos.app.domain.model.MessageResult?>? {
        val outcome = executeToolInner(
            ToolParser.ToolCallData(ToolNames.ADD_MEMORY, ToolArguments.of(mapOf("content" to content))),
            sessionId,
            allowedTools,
            userText = "",
            previousAssistant = null,
            guardFact = false
        )
        auditTrailService.logToolCall(sessionId = sessionId, toolName = ToolNames.ADD_MEMORY, resultJson = outcome.resultJson, note = ANSWERED_MEMO_NOTE)
        return when {
            outcome.rejected -> rejectionReply(ToolNames.ADD_MEMORY) to null
            outcome.succeeded -> MEMO_SAVED to extractResult(outcome.resultJson)
            else -> null
        }
    }

    private suspend fun executeToolInner(
        call: ToolParser.ToolCallData,
        sessionId: String,
        allowedTools: List<String>,
        userText: String,
        previousAssistant: String? = null,
        guardFact: Boolean = true
    ): ToolOutcome {
        // [WHY] allowlist가 프롬프트 텍스트에만 있으면 모델(또는 주입된 문서)이 임의 툴을 호출해도
        // 실행되므로, 실행 시점에 반드시 재검증한다.
        if (call.name !in allowedTools) {
            auditTrailService.logError(sessionId, "Blocked tool call outside allowlist: ${call.name}")
            val message = if (call.name == ToolNames.SEARCH_WIKIPEDIA) {
                "웹 검색이 비활성화되어 있습니다. 사용자에게 채팅 화면 상단의 웹 검색 토글을 켜도록 안내하세요."
            } else {
                "이 에이전트에서 사용할 수 없는 도구입니다: ${call.name}"
            }
            return ToolOutcome(ToolResultJson.error(message), executed = false)
        }

        val executor = toolRegistry.getExecutor(call.name)
            ?: return ToolOutcome(ToolResultJson.error("알 수 없는 Tool입니다: ${call.name}"), executed = false)

        // [WHY] 되묻기("송장번호 다시 알려줘")에 불린 메모 저장은 승인 시트를 띄우지 않고 막는다 — 프롬프트로는 안 고쳐졌고(exp61),
        // 시트에서 취소하면 물어본 값 대신 "메모에는 남기지 않았어요."로 끝났다. 결과 문구는 exp61c G2(막은 턴 3/3 값을 답함, 거짓 저장 주장 0).
        if (call.name == ToolNames.ADD_MEMORY && com.kosmos.app.domain.memory.RepeatRequest.matches(userText)) {
            return ToolOutcome(ToolResultJson.error(REPEAT_NOT_SAVE), executed = false)
        }

        // [WHY] 저장 낱말 없이 사실만 말한 턴의 메모 저장은 막고 먼저 묻게 한다 — 대화 기록에 끌린 호출이라 프롬프트로는 못 막았다(exp64, 0.38.9 결정 1).
        if (guardFact && call.name == ToolNames.ADD_MEMORY &&
            com.kosmos.app.domain.memory.MemoSaveIntent.shouldBlock(userText, previousAssistant)
        ) {
            return ToolOutcome(ToolResultJson.error(com.kosmos.app.domain.memory.MemoSaveIntent.ASK_FIRST), executed = false, factBlocked = true)
        }

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
                    return ToolOutcome(ToolResultJson.error("사용자가 승인하지 않았어요"), executed = false, rejected = true)
                }
                auditTrailService.logApprovalGranted(sessionId, "${call.name}: ${approvalRequest.description}")
            }
            ToolOutcome.of(executor.execute(call.args, sessionId), executed = true)
        } catch (e: com.kosmos.app.assistant.tool.ToolArgumentException) {
            auditTrailService.logError(sessionId, "Invalid tool argument: ${call.name}.${e.field} (${e.reason})")
            ToolOutcome(toolArgumentErrorJson(call.name, e), executed = false)
        }
    }

    /**
     * 승인 거절(시간 초과 포함) 때 화면에 남는 답 — 모델이 쓰지 않는다.
     *
     * [WHY] 모델 회신 문구로는 다섯 판(exp38d·e, exp49, exp56 R0~R4)을 재도 거짓 성공·오류로 바꿈·앞 턴 따라 하기 중 하나가 남았다.
     */
    private fun rejectionReply(toolName: String): String = when (toolName) {
        ToolNames.ADD_SCHEDULE -> "일정을 추가하지 않았어요."
        ToolNames.ADD_REMINDER -> "알림을 등록하지 않았어요."
        // [WHY] "저장하지 않았어요"는 다음 턴에 "그 정보는 없다"로 읽혀, 바로 앞에서 들은 값을 "찾지 못했어요"라고 했다(실기기 T7, exp60 2/6 → 5/6).
        ToolNames.ADD_MEMORY -> "메모에는 남기지 않았어요."
        else -> "요청을 실행하지 않았어요."
    }

    /** 툴 결과 JSON 에서 meta.episodeIds 를 뽑습니다. 형식이 어긋나면 빈 목록 — 칩이 안 뜰 뿐이다. */
    private fun extractEpisodeIds(resultJson: String): List<String> = runCatching {
        val meta = org.json.JSONObject(resultJson).optJSONObject("meta") ?: return emptyList()
        val ids = meta.optJSONArray("episodeIds") ?: return emptyList()
        (0 until ids.length()).map { ids.getString(it) }
    }.getOrDefault(emptyList())

    /** 실행기가 `meta.result` 에 실은 결과 카드 대상(0.38.2). 없거나 모르는 종류면 null. */
    private fun extractResult(resultJson: String): com.kosmos.app.domain.model.MessageResult? = runCatching {
        val result = org.json.JSONObject(resultJson).optJSONObject("meta")?.optJSONObject("result") ?: return null
        val kind = com.kosmos.app.domain.model.MessageResult.Kind.entries.firstOrNull { it.name == result.optString("kind") }
            ?: return null
        com.kosmos.app.domain.model.MessageResult(kind, result.optString("ref"), result.optString("meta").ifEmpty { null })
    }.getOrNull()

    /** 모델에 되돌릴 툴 결과에서 meta 필드를 제거합니다. 파싱이 안 되면 원문 그대로. */
    private fun stripMeta(resultJson: String): String = runCatching {
        val json = org.json.JSONObject(resultJson)
        if (!json.has("meta")) return resultJson
        json.remove("meta")
        json.toString()
    }.getOrDefault(resultJson)

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
            // [WHY] 깨진 원문 값은 안내에 에코하지 않는다 — 깨짐이 생성/디코딩 계층에서 온다는
            // 것이 실기기 관측이므로(2026-08-13), 깨진 토큰열을 문맥에 다시 넣으면 모델이 그
            // 형태를 따라 쓸 재료만 는다. 올바른 예시 형식만 제시한다.
            com.kosmos.app.assistant.tool.ToolArgumentException.Reason.BAD_FORMAT ->
                "'${e.field}' 인자가 ISO 8601 일시 형식이 아닙니다. '2026-08-17T15:00:00' 형식으로 다시 보내세요."
            // [WHY] AddReminderToolExecutor 의 실행 오류 문구와 같은 원문 — 승인 전으로 당겼을 뿐
            // 모델이 받는 지시는 같아야 한다(exp40).
            com.kosmos.app.assistant.tool.ToolArgumentException.Reason.PAST ->
                "이미 지난 시각입니다. 미래의 시각으로 다시 호출하세요."
        }
        return org.json.JSONObject()
            .put(ToolResultJson.STATUS, ToolResultJson.ERROR)
            .put("tool", toolName)
            .put("field", e.field)
            .put("reason", e.reason.name.lowercase())
            .put("message", guidance)
            .toString()
    }

    private companion object {
        // [WHY] 3 은 SearchMemory 재조회 프로토콜의 하한이다 — 검색(1) → 재검색어로 재조회(2)
        // → 최종 답변(3). 줄이면 그 프로토콜이 조용히 끊긴다. 인자 오류 자가수정도 이 상한을
        // 공유한다(BAD_FORMAT 재시도).
        const val MAX_TOOL_LOOP_COUNT = 3

        // exp61c G2 실측 원문.
        const val REPEAT_NOT_SAVE = "저장하지 않았습니다. 사용자는 앞에서 말한 내용을 다시 듣고 싶어 합니다. 그 값을 답하고, 저장했다고 말하지 마세요."

        // 앞 턴(비서 말·그 앞 사용자 말)을 찾을 때 읽는 최근 메시지 수 — 지금 턴의 사용자 메시지·문서 블록을 넘고도 남는다.
        const val PREVIOUS_TURN_SCAN = 6

        // 앱이 연 메모 저장이 승인됐을 때의 답 — 거절 문장 "메모에는 남기지 않았어요."와 짝.
        const val MEMO_SAVED = "메모에 저장했어요."

        const val ANSWERED_MEMO_NOTE = "저장 물음에 대한 긍정 — 앱이 연 저장"

        // "<|think|" 태그가 토큰 경계에 걸려도 감지되는 길이
        const val THINK_TAG_WINDOW = 12

    }
}
