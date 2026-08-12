package com.kosmos.app.runtime.gemma

import android.content.Context
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import android.util.Log
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelToolCall
import com.kosmos.app.domain.modelrunner.ModelTurn
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.kosmos.app.di.LLMDispatcher

import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector

/**
 * 엔진 설정을 조립합니다. 챗·비전은 넘겨받은 백엔드를 쓰고 **오디오는 CPU 고정**입니다.
 *
 * [WHY] 이 조립을 `ensureInferenceInitialized` 안에 인라인으로 두었을 때 `audioBackend` 가
 * 빠진 것을 **어떤 테스트도 잡지 못했다.** 음성 경로 테스트(`TranscribeAudioUseCaseTest` 7건)는
 * `ModelRunner` 를 목으로 대체했는데, 잘못 설정된 것이 정확히 그 목이 가린 컴포넌트였다 —
 * 통과하는 테스트가 동작할 수 없는 경로를 검증하고 있었다. 순수 함수로 꺼내면 네이티브도 목도
 * 거치지 않고 설정 자체를 단언할 수 있다.
 *
 * [WHY] 오디오를 CPU 로 고정하는 근거는 gallery 다 —
 * `LlmChatModelHelper.kt:129` 가 `audioBackend = … Backend.CPU()` 에
 * `must be CPU for Gemma 3n` 주석을 달아 두었다. 비전은 GPU 로 두어도 동작한다.
 *
 * [WHY] `cacheDir` 을 CPU 폴백에도 넘긴다. 예전에는 GPU 경로만 받아서, 폴백으로 떨어진 기기는
 * 매 실행마다 커널 캐시를 다시 만들었다.
 *
 * [WHY] `maxNumTokens` 를 **명시 전달한다.** 예전에는 넘기지 않았고 그러면 런타임 기본값(4096)이
 * 되는데, 앱은 프리필 예산을 6000(슬라이더 최대 8000)으로 잡아 **용량을 넘는 프롬프트를 만들고
 * 있었다.** 넘기지 않으면 그 값을 Kotlin 쪽에서 볼 수 없어 예산과의 어긋남이 드러나지도 않는다
 * (자세한 근거는 [Constants.ENGINE_MAX_TOKENS]).
 */
internal fun buildEngineConfig(
    modelPath: String,
    backend: Backend,
    cacheDir: String
): EngineConfig = EngineConfig(
    modelPath = modelPath,
    backend = backend,
    visionBackend = backend,
    audioBackend = Backend.CPU(),
    maxNumTokens = Constants.ENGINE_MAX_TOKENS,
    cacheDir = cacheDir
)

/**
 * [GemmaModelRunner]
 * Google Edge LiteRT-LM을 사용하여 온디바이스 Gemma 모델(LLM)을 실행하는 런타임 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Runtime / Infra
 * - **Dependencies**: [GemmaRuntimeManager], [RuntimeMetricsCollector], LiteRT Engine
 *
 * ### Key Flow
 * 1. [ChatPrompt] 객체를 입력받아 모델 초기화(엔진 및 GPU 백엔드) 확인
 * 2. LiteRT-LM [Conversation] 객체 생성 또는 기존 세션 유지
 * 3. 입력 텍스트(또는 텍스트+이미지)를 모델에 전달하여 스트리밍(또는 단일) 추론 수행
 * 4. 추론 중 기기 온도/리소스 메트릭 수집 및 스레드 병목 방지(Yield) 적용
 */
@Singleton
class GemmaModelRunner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val runtimeManager: GemmaRuntimeManager,
    private val metricsCollector: RuntimeMetricsCollector,
    @LLMDispatcher private val llmDispatcher: CoroutineDispatcher
) : ModelRunner {

    companion object {
        // [WHY] Gemma 4 는 thinking 모델이라 템플릿 기본값으로 생각 모드가 켜진다. gallery 는
        // **모든** 추론 호출에서 extraContext 로 enable_thinking=false 를 넘기고, 허용 목록에서도
        // thinking 과 agent chat(툴 호출)을 조합하지 않는다 — 생각 모드에서는 함수호출 동작이
        // 달라진다. 툴 호출이 핵심 기능이므로 gallery 와 같은 기본값을 따른다.
        private val EXTRA_CONTEXT: Map<String, Any> = mapOf("enable_thinking" to false)
    }

    override val loadState: StateFlow<ModelLoadState> = runtimeManager.loadState

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var currentSessionId: String? = null
    private var currentSystemInstruction: String? = null
    private var currentEnabledTools: List<String>? = null

    // [WHY] llmDispatcher(limitedParallelism(1))는 suspension point에서 코루틴이 교차될 수 있어
    // 단독으로는 상호배제가 아니다. 엔진/대화 수명주기(초기화·생성·해제)는 뮤텍스로 직렬화한다.
    private val lifecycleMutex = kotlinx.coroutines.sync.Mutex()
    private val closeScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + llmDispatcher
    )

    override suspend fun generate(
        prompt: ChatPrompt,
        onToken: ((String) -> Unit)?
    ): AppResult<ModelTurn> = runTurn(prompt, onToken, "추론 중 알 수 없는 오류 발생") {
        // 텍스트 전용 턴은 별도 Content 조립이 필요 없다.
        emptyList()
    }

    override suspend fun generateWithImage(
        prompt: ChatPrompt,
        imageBytes: ByteArray,
        onToken: ((String) -> Unit)?
    ): AppResult<ModelTurn> = runTurn(prompt, onToken, "멀티모달 추론 중 오류 발생") {
        // [WHY] 이미지를 텍스트보다 앞에 넣는다 — 마지막 토큰이 텍스트여야 응답 품질이 안정적이다.
        listOf(
            com.google.ai.edge.litertlm.Content.ImageBytes(imageBytes),
            com.google.ai.edge.litertlm.Content.Text(prompt.currentInput)
        )
    }

    /**
     * [WHY] `currentInput` 이 비어 있으면 **텍스트 파트를 아예 붙이지 않는다.** 전사에서 지시
     * 문장을 함께 보내면, 무음 오디오일 때 모델이 **그 지시문을 그대로 되읊는다** — PC 실험
     * (exp16)에서 무음 2초에 `"이 오디오를 들리는 그대로 받아써 주세요."` 가 전사문으로 나왔다.
     * 빈 문자열이 아니므로 `TranscribeAudioUseCase` 의 공백 검사를 통과해, 앱이 그 문장을
     * **사용자 메시지로 저장하고 그것에 답한다**(PRD EC3 위반).
     *
     * 되읊음을 문자열로 걸러내는 대신 되읊을 대상을 없앤다. 오디오만 보내도 전사는 정상이고
     * 무음에서는 빈 결과가 온다(exp16 실측).
     */
    override suspend fun generateWithAudio(
        prompt: ChatPrompt,
        audioPath: String,
        onToken: ((String) -> Unit)?
    ): AppResult<ModelTurn> = runTurn(prompt, onToken, "음성 추론 중 오류 발생") {
        buildList {
            add(com.google.ai.edge.litertlm.Content.AudioFile(audioPath))
            if (prompt.currentInput.isNotBlank()) {
                add(com.google.ai.edge.litertlm.Content.Text(prompt.currentInput))
            }
        }
    }

    /**
     * 세 진입점(텍스트/이미지/오디오)의 공통 추론 흐름입니다.
     *
     * [WHY] 이전에는 같은 60여 줄이 세 번 복제돼 있었다. 툴 호출 수집을 추가하면서 세 곳을
     * 각각 고치면 어긋날 수밖에 없으므로 한 곳으로 모았다 — 실제로 이미지 경로에는 오디오
     * 경로에 없는 죽은 try/catch 가 남아 있었다.
     *
     * @param extraContents 비어 있으면 [ChatPrompt.currentInput] 을 텍스트로 보낸다.
     */
    private suspend fun runTurn(
        prompt: ChatPrompt,
        onToken: ((String) -> Unit)?,
        failureMessage: String,
        extraContents: () -> List<com.google.ai.edge.litertlm.Content>
    ): AppResult<ModelTurn> = withContext(llmDispatcher) {
        val currentState = loadState.value
        if (currentState !is ModelLoadState.Ready) {
            return@withContext AppResult.Failure(AppError.ModelNotReady("Model is not ready"))
        }

        try {
            lifecycleMutex.withLock {
                ensureInferenceInitialized(currentState.modelInfo.modelPath)

                // [WHY] 임계 발열(≥48°C) 시 추론을 진행하면서 감사 로그만 남기던 문제 수정 —
                // 사전 조건 실패면 실제로 추론을 중단하고 오류를 반환한다.
                val preconditionResult = metricsCollector.checkPreconditions()
                if (preconditionResult is AppResult.Failure) {
                    return@withLock AppResult.Failure(preconditionResult.error)
                }
                metricsCollector.handleCooldownIfNecessary()
                metricsCollector.recordStart()
                val startTime = System.currentTimeMillis()

                val currentConversation = getOrCreateConversation(prompt)
                val outgoing = buildMessage(prompt, extraContents())

                // [WHY] 부수 계산용 임시 대화는 여기서 반드시 닫는다. 캐시하지 않으므로 이
                // 시점을 놓치면 네이티브 자원이 그대로 샌다.
                try {
                if (onToken != null) {
                    var finalResponse = ""
                    val toolCalls = mutableListOf<ModelToolCall>()
                    var error: Throwable? = null
                    // [WHY] 네이티브가 토큰 하나당 메시지 하나를 보내므로, 이 수가 곧 생성 토큰
                    // 수다. 상단 상태 표시의 tok/s 가 여기서 나온다 (ADR-015).
                    var tokenCount = 0

                    try {
                        // [WHY] **토큰 유실 방지.** 0.14.0 의 `sendMessageAsync` 는 callbackFlow 이고,
                        // 네이티브 콜백(`Conversation$sendMessageAsync$1$1.onMessage`)이
                        // `ProducerScope.trySend(message)` 를 호출한 뒤 **반환값을 버린다**(바이트코드
                        // 확인). callbackFlow 의 기본 용량은 64 이고, 가득 차면 `trySend` 는 예외도
                        // 로그도 없이 실패한다 — 그 토큰은 사라진다. 수집이 조금이라도 느리면
                        // 답변 중간중간의 글자가 조용히 빠진다("2015년 10월" → "205년 10").
                        //
                        // 무한 버퍼를 끼우면 이 Flow 의 소비자는 버퍼 연산자가 되어 즉시 비워 가고,
                        // 우리 본문이 얼마나 느리든 네이티브 채널이 넘치지 않는다.
                        currentConversation.sendMessageAsync(outgoing, EXTRA_CONTEXT)
                            .buffer(kotlinx.coroutines.channels.Channel.UNLIMITED)
                            .collect { message ->
                                yield() // CPU 점유율 양보 (UI 스레드 기아 방지)
                                collectToolCalls(message, toolCalls)
                                val token = message.contents.contents
                                    .filterIsInstance<com.google.ai.edge.litertlm.Content.Text>()
                                    .joinToString("") { it.text }
                                if (token.isNotEmpty()) {
                                    onToken.invoke(token)
                                    finalResponse += token
                                    tokenCount++
                                }
                            }
                    } catch (e: Exception) {
                        error = e
                    }

                    metricsCollector.recordEnd(System.currentTimeMillis() - startTime, tokenCount)

                    if (error != null) {
                        AppResult.Failure(AppError.ModelInferenceError(error.message ?: "스트리밍 중 에러 발생"))
                    } else {
                        logTurn(prompt, finalResponse, toolCalls)
                        AppResult.Success(ModelTurn(finalResponse, toolCalls))
                    }
                } else {
                    val message = currentConversation.sendMessage(outgoing, EXTRA_CONTEXT)
                    val toolCalls = mutableListOf<ModelToolCall>()
                    collectToolCalls(message, toolCalls)
                    val messageText = message.contents.contents
                        .filterIsInstance<com.google.ai.edge.litertlm.Content.Text>()
                        .joinToString("") { it.text }

                    metricsCollector.recordEnd(System.currentTimeMillis() - startTime)

                    // [WHY] 텍스트가 비어도 툴 호출만 온 턴은 정상이다 — 모델이 말 없이 곧바로
                    // 툴을 부르는 경우가 흔하다. 둘 다 비었을 때만 실패로 본다.
                    if (messageText.isNotEmpty() || toolCalls.isNotEmpty()) {
                        logTurn(prompt, messageText, toolCalls)
                        AppResult.Success(ModelTurn(messageText, toolCalls))
                    } else {
                        AppResult.Failure(AppError.ModelInferenceError("응답 생성 결과가 null입니다."))
                    }
                }
                } finally {
                    if (prompt.oneShot) runCatching { currentConversation.close() }
                }
            }
        } catch (e: Exception) {
            AppResult.Failure(AppError.ModelInferenceError("$failureMessage: ${e.message}"))
        }
    }

    private fun buildMessage(
        prompt: ChatPrompt,
        extras: List<com.google.ai.edge.litertlm.Content>
    ): Message {
        // [WHY] 툴 실행 결과는 사용자 발화가 아니라 **TOOL 역할 메시지**로 보내야 모델 템플릿의
        // 역할과 맞는다. Contents 오버로드는 무조건 Message.user 로 감싸므로(바이트코드 확인)
        // 여기서 직접 Message.tool 로 감싼다 — 공식 문서의 수동 툴 호출 예제와 같은 형태다.
        prompt.toolResponse?.let { response ->
            return Message.tool(
                Contents.of(
                    com.google.ai.edge.litertlm.Content.ToolResponse(response.name, response.resultJson)
                )
            )
        }
        if (extras.isNotEmpty()) {
            return Message.user(Contents.of(*extras.toTypedArray()))
        }
        return Message.user(
            Contents.of(com.google.ai.edge.litertlm.Content.Text(prompt.currentInput))
        )
    }

    /**
     * [WHY] 스트리밍 중 같은 툴 호출이 여러 메시지에 걸쳐 반복 노출될 수 있으므로 이름+인자로
     * 중복을 제거한다.
     */
    private fun collectToolCalls(
        message: com.google.ai.edge.litertlm.Message,
        into: MutableList<ModelToolCall>
    ) {
        message.toolCalls.forEach { call ->
            val mapped = ModelToolCall(
                name = KosmosToolDeclarations.canonicalName(call.name),
                // [WHY] 인자 키도 선언과 같은 snake_case 로 도착한다(start_time 등). executor 는
                // camelCase 를 읽으므로 여기서 되돌리지 않으면 인자가 통째로 유실된다.
                args = call.arguments.mapKeys { (key, _) -> KosmosToolDeclarations.camelArgName(key) }
            )
            if (into.none { it.name == mapped.name && it.args == mapped.args }) {
                into += mapped
            }
        }
    }

    // [WHY] 툴 호출 여부는 실기기에서만 확인할 수 있고 감사 로그에는 시스템 지시가 남지 않는다.
    // enabledTools 를 함께 남겨야 toolCalls=[] 가 "선언 안 됨"인지 "모델이 거부"인지 구분된다.
    private fun logTurn(prompt: ChatPrompt, text: String, toolCalls: List<ModelToolCall>) {
        Log.d(
            "GemmaModelRunner",
            "turn done: textLen=${text.length}, toolCalls=${toolCalls.map { it.name }}, " +
                "enabledTools=${prompt.enabledTools}"
        )
    }

    override suspend fun cancel() {
        // [WHY] cancelProcess는 진행 중 스트리밍을 중단시키기 위한 호출이므로 뮤텍스를 잡지 않는다
        // (생성 본문이 뮤텍스를 보유 중이어도 취소가 가능해야 함).
        conversation?.cancelProcess()
    }

    override fun close() {
        // [WHY] 메인 스레드에서 네이티브 close를 직접 부르면 진행 중 추론과 경합(use-after-free)하고
        // blocking으로 ANR 위험이 있다. 진행 중 생성에 취소를 요청한 뒤, LLM 디스패처에서
        // 뮤텍스로 직렬화하여(현재 생성 종료 후) 해제한다.
        runCatching { conversation?.cancelProcess() }
        closeScope.launch {
            lifecycleMutex.withLock {
                runCatching { conversation?.close() }
                conversation = null
                runCatching { engine?.close() }
                engine = null
                currentSessionId = null
                currentSystemInstruction = null
                currentEnabledTools = null
            }
        }
    }

    override suspend fun warmUp() {
        runtimeManager.checkModelFile() // Re-check file existence in case user just added it
        val currentState = runtimeManager.loadState.value
        if (currentState is ModelLoadState.FileFound) {
            runtimeManager.setInitializing()
            // [WHY] Dispatchers.IO에서 초기화하면 llmDispatcher의 첫 generate와 이중 초기화 경합이
            // 발생해 Engine이 누수된다. 동일 디스패처 + 뮤텍스로 직렬화한다.
            withContext(llmDispatcher) {
                lifecycleMutex.withLock {
                    ensureInferenceInitialized(currentState.modelInfo.modelPath)
                }
            }
            runtimeManager.setReady(currentState.modelInfo)
        }
    }

    private fun ensureInferenceInitialized(modelPath: String) {
        if (engine == null) {
            // [WHY] 네이티브(liblitertlm_jni)의 로그는 기본적으로 억제되어 있어 constrained
            // decoding 문법 생성 실패, 데이터 프로세서 선택 같은 결정적 진단이 logcat 에 전혀
            // 남지 않았다. 툴 호출 디버깅에는 그 노출이 결정적이었으므로 디버그 빌드에서는
            // 유지하고, 릴리스에서는 네이티브 기본값(억제)으로 되돌린다 — INFO 는 토큰 단위로
            // 쏟아져 정작 필요한 로그를 밀어낸다(실기기에서 확인).
            val severity = if (com.kosmos.app.BuildConfig.DEBUG) {
                com.google.ai.edge.litertlm.LogSeverity.INFO
            } else {
                com.google.ai.edge.litertlm.LogSeverity.ERROR
            }
            runCatching { Engine.setNativeMinLogSeverity(severity) }
            val cacheDir = context.cacheDir.absolutePath
            try {
                // S25 Ultra 등 GPU Delegate 활성화로 최적화
                val newEngine = Engine(buildEngineConfig(modelPath, Backend.GPU(), cacheDir))
                newEngine.initialize()
                engine = newEngine
                Log.d("GemmaModelRunner", "Engine initialized with GPU backend")
            } catch (e: Exception) {
                Log.e("GemmaModelRunner", "Failed to initialize GPU backend, falling back to CPU", e)
                val fallbackEngine = Engine(buildEngineConfig(modelPath, Backend.CPU(), cacheDir))
                fallbackEngine.initialize()
                engine = fallbackEngine
                Log.d("GemmaModelRunner", "Engine initialized with CPU backend")
            }
        }
    }

    // [WHY] ExperimentalFlags 와 renderPrefaceIntoString 은 @ExperimentalApi — gallery 도 같은
    // 플래그를 쓰므로 감수한다. 0.14.0 업그레이드 시점마다 존재 여부를 컴파일이 검증해 준다.
    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    private fun getOrCreateConversation(prompt: ChatPrompt): Conversation {
        val currentEngine = engine ?: throw IllegalStateException("Engine is not initialized")

        // [WHY] 부수 계산(음성 전사, 일정 요약)은 시스템 지시와 sessionId 가 채팅과 다르므로,
        // 캐시된 대화에 섞으면 재사용 판정이 깨져 채팅 전체가 다시 프리필된다(ADR-010).
        // 캐시를 **건드리지 않고** 임시 대화를 만들어 돌려준다 — 호출자가 닫는다.
        if (prompt.oneShot) return createOneShotConversation(currentEngine, prompt)

        val existing = conversation
        val isSameSession = currentSessionId == prompt.sessionId
        // [WHY] 응답 스타일 설정 등으로 시스템 지시가 달라지면 동일 세션이라도 대화를 재생성해야
        // 모델이 새 지시를 본다.
        val isSameSystemInstruction = currentSystemInstruction == prompt.systemInstruction
        // [WHY] 선언된 툴이 달라지면(웹 검색 토글 등) 대화를 재생성해야 모델이 새 툴 목록을 본다.
        val isSameTools = currentEnabledTools == prompt.enabledTools

        // [WHY] 툴 응답 턴은 **반드시** 직전 턴의 호출 문맥이 있는 대화로 돌아가야 한다. 0.8.5
        // 실기기에서 토큰 초과 판정이 승인 대기 사이에 대화를 재생성해, 모델이 "자기가 호출한
        // 적 없는 툴"의 응답을 받고 뜬금없는 답을 만들었다. 이 턴만큼은 어떤 재생성 조건보다
        // 재사용이 우선한다.
        if (prompt.toolResponse != null && existing != null && isSameSession && isSameTools) {
            return existing
        }

        // [WHY] 임계값을 사용자 설정(프리필 예산)에서 파생시킨다. 예전에는 런타임에 박힌
        // 8000 이어서, 설정에서 예산을 내려도 살아 있는 대화의 KV 는 8000 토큰까지 자랐다 —
        // 설정이 메모리에 아무 영향을 주지 못했다.
        //
        // [WHY] 하한이 필요하다. 예산 최소값(1000)을 그대로 쓰면 시스템 지시 + 툴 선언만으로
        // 이미 임계값을 넘어 **매 턴 재생성**되고, 재생성이야말로 우리가 없애려는 비용이다.
        //
        // [WHY] 임계값을 엔진 KV 천장으로도 묶는다. 예전에는 하한이 4000 이라 천장(3328)보다 컸고,
        // 그러면 예산을 낮춰도 임계값이 밀려 올라가 **대화가 용량을 넘도록 자라는 것을 허용**했다 —
        // 재생성을 막으려는 하한이 오히려 초과를 보장했다 (Constants.ENGINE_MAX_TOKENS 참조).
        val resetThreshold = prompt.contextBudgetTokens
            .coerceAtLeast(Constants.MIN_CONVERSATION_RESET_TOKENS)
            .coerceAtMost(Constants.PREFILL_CEILING_TOKENS)
        val existingTokens = existing?.getTokenCount() ?: 0
        val isTokenExceeded = existing != null && existingTokens > resetThreshold

        // [WHY] 초과를 조용히 넘기지 않는다. AAR 0.14.0 은 KV 용량을 넘겨도 오류를 내지 않고
        // 초과분을 처리해 버리는 것이 확인됐다(파이썬 0.15.0 은 `5857 >= 4096` 으로 거부한다).
        // 그래서 이 경계는 크래시가 아니라 **품질 저하**로만 드러나고, 그 형태로는 원인을 찾기
        // 어렵다. 디버그 빌드에서 경계에 닿는 것을 눈에 보이게 남긴다.
        if (com.kosmos.app.BuildConfig.DEBUG && existingTokens > Constants.PREFILL_CEILING_TOKENS) {
            Log.w(
                "GemmaModelRunner",
                "KV 천장 초과: tokens=$existingTokens > ceiling=${Constants.PREFILL_CEILING_TOKENS} " +
                    "(engine=${Constants.ENGINE_MAX_TOKENS}). 예산·오버헤드 상수를 다시 볼 것."
            )
        }

        if (existing != null && isSameSession && isSameSystemInstruction && isSameTools && !isTokenExceeded) {
            return existing
        }

        existing?.close()

        val initialMessages = buildList {
            addAll(fewShotToolExample(prompt.enabledTools))
            prompt.history.forEach {
                add(if (it.role.name == "USER") Message.user(it.content) else Message.model(it.content))
            }
        }

        val config = ConversationConfig(
            systemInstruction = Contents.of(prompt.systemInstruction),
            initialMessages = initialMessages,
            // [WHY] 툴 선언을 런타임에 넘겨 모델의 정식 함수호출 템플릿으로 주입한다.
            // 시스템 프롬프트에 형식을 글로 설명하던 방식은 실기기에서 무시됐다 (ADR-008).
            tools = KosmosToolDeclarations.providersFor(prompt.enabledTools),
            // [WHY] false 여야 런타임이 툴을 스스로 실행하지 않고 우리에게 호출을 넘긴다 —
            // 승인 다이얼로그(PRD F4)를 거쳐야 하므로 자동 실행을 쓸 수 없다.
            automaticToolCalling = false,
            // [WHY] gallery 는 agent chat(툴 호출) 진입 시 topK=1(greedy)을 **강제**한다
            // (AgentChatSamplingParamsManager — "specifically enforcing greedy decoding").
            // 샘플링이 남아 있으면 호출 시작 토큰이 최빈이 아닐 때 툴 호출이 확률적으로
            // 뭉개진다. greedy 는 숫자 왜곡("1234"→"12", 0.8.3 실기기)도 함께 막는다.
            // topK=1 에서 temperature/topP 는 효력이 없으나 gallery 기본값을 그대로 둔다.
            samplerConfig = SamplerConfig(
                temperature = 1.0,
                topK = 1,
                topP = 0.95
            )
        )
        // [WHY] gallery 는 툴을 쓰는 모든 태스크에서 이 전역 플래그를 createConversation 직전에
        // 켜고 직후에 끈다. 이 플래그가 툴 스키마로부터 FST 문법을 만들어 모델 출력을 호출
        // 구문으로 강제한다 — 선언만으로는 4B 모델이 호출 형식을 지키지 못해 toolCalls 가
        // 비었다(0.8.0 실기기). ConversationConfig 필드가 아니라 생성 시점에만 읽히는 전역이다.
        ExperimentalFlags.enableConversationConstrainedDecoding = prompt.enabledTools.isNotEmpty()
        val newConversation = try {
            currentEngine.createConversation(config)
        } finally {
            ExperimentalFlags.enableConversationConstrainedDecoding = false
        }
        logConversationCreated(prompt, newConversation)
        conversation = newConversation
        currentSessionId = prompt.sessionId
        currentSystemInstruction = prompt.systemInstruction
        currentEnabledTools = prompt.enabledTools
        return newConversation
    }

    /**
     * 부수 계산 전용 임시 대화입니다. **호출자가 닫아야 합니다.**
     *
     * [WHY] 툴도 few-shot 도 히스토리도 싣지 않는다. 그래서 채팅 대화보다 훨씬 싸다 —
     * 툴 선언만 실측 ~2천 토큰이므로 그것이 빠지면 프리필이 짧은 시스템 지시 한 줄뿐이다.
     * 부수 계산에 툴을 줄 이유도 없다(전사·요약은 도구를 쓰지 않는다).
     */
    private fun createOneShotConversation(currentEngine: Engine, prompt: ChatPrompt): Conversation =
        currentEngine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(prompt.systemInstruction),
                automaticToolCalling = false,
                samplerConfig = SamplerConfig(temperature = 1.0, topK = 1, topP = 0.95)
            )
        )

    /**
     * 툴 호출의 few-shot 시범입니다. 대화 서두에 "사용자 요청 → 모델의 실제 툴 호출 →
     * 툴 응답 → 확인 답변" 왕복 한 번을 심어 둡니다.
     *
     * [WHY] 0.8.1~0.8.4 실기기에서 선언·constrained decoding·greedy·지목 프롬프트를 모두
     * 갖춰도 모델이 호출 대신 말로만 약속했다("기억해 두겠습니다" — 호출 0회). 지시만으로
     * 성향이 안 바뀌는 4B 모델에게 남은 가장 강한 지렛대는 **정확한 네이티브 형식의 시범**이다.
     * 예시는 LLM 대화에만 존재하고 DB 나 화면 기록에는 남지 않는다.
     */
    private fun fewShotToolExample(enabledTools: List<String>): List<Message> {
        if (!enabledTools.contains("AddMemory")) return emptyList()
        return listOf(
            Message.user("내 자물쇠 비밀번호는 8282야, 기억해줘"),
            Message.model(
                Contents.of(com.google.ai.edge.litertlm.Content.Text("")),
                listOf(
                    com.google.ai.edge.litertlm.ToolCall(
                        "add_memory",
                        mapOf(
                            "content" to "자물쇠 비밀번호는 8282",
                            "tags" to listOf("비밀번호", "자물쇠")
                        )
                    )
                ),
                emptyMap()
            ),
            Message.tool(
                Contents.of(
                    com.google.ai.edge.litertlm.Content.ToolResponse(
                        "add_memory",
                        """{"status":"success"}"""
                    )
                )
            ),
            Message.model("자물쇠 비밀번호 8282를 기억해 두었습니다.")
        )
    }

    // [WHY] toolCalls=[] 만으로는 "선언이 템플릿에 안 들어감"과 "모델이 호출을 거부"를 구분할 수
    // 없다. 렌더링된 프리페이스에 툴 블록이 있는지가 모델 파일의 템플릿 지원 여부를 실기기에서
    // 확정하는 유일한 단서다 (Gemma 3n 계열 템플릿에는 툴 블록이 없다).
    //
    // [WHY] 디버그 빌드로 한정한다. `renderPrefaceIntoString()` 은 템플릿 전체를 실제로
    // 렌더링하는 작업이고, 그것도 LLM 디스패처의 수명주기 뮤텍스 안에서 일어난다 — 릴리스에서
    // 매 재생성마다 낼 비용이 아니다. 프리페이스 2천 자를 logcat 에 남기는 것 자체도
    // 사용자 발화와 검색된 기억이 로그로 새는 경로다.
    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    private fun logConversationCreated(prompt: ChatPrompt, created: Conversation) {
        if (!com.kosmos.app.BuildConfig.DEBUG) return
        Log.d(
            "GemmaModelRunner",
            "conversation created: tools=${prompt.enabledTools}, " +
                "providers=${KosmosToolDeclarations.providersFor(prompt.enabledTools).size}"
        )
        runCatching { created.renderPrefaceIntoString() }
            .onSuccess { preface -> Log.d("GemmaModelRunner", "preface: ${preface.take(2000)}") }
            .onFailure { e -> Log.d("GemmaModelRunner", "preface render failed: ${e.message}") }
    }
}
