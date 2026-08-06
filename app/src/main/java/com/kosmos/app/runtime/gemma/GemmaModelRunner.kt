package com.kosmos.app.runtime.gemma

import android.content.Context
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import android.util.Log
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelToolCall
import com.kosmos.app.domain.modelrunner.ModelTurn
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
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
    @ApplicationContext private val context: Context,
    private val runtimeManager: GemmaRuntimeManager,
    private val metricsCollector: RuntimeMetricsCollector,
    @LLMDispatcher private val llmDispatcher: CoroutineDispatcher
) : ModelRunner {

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
        imageTokenBudget: Int,
        onToken: ((String) -> Unit)?
    ): AppResult<ModelTurn> = runTurn(prompt, onToken, "멀티모달 추론 중 오류 발생") {
        // [WHY] 이미지를 텍스트보다 앞에 넣는다 — 마지막 토큰이 텍스트여야 응답 품질이 안정적이다.
        listOf(
            com.google.ai.edge.litertlm.Content.ImageBytes(imageBytes),
            com.google.ai.edge.litertlm.Content.Text(prompt.currentInput)
        )
    }

    override suspend fun generateWithAudio(
        prompt: ChatPrompt,
        audioPath: String,
        onToken: ((String) -> Unit)?
    ): AppResult<ModelTurn> = runTurn(prompt, onToken, "음성 추론 중 오류 발생") {
        listOf(
            com.google.ai.edge.litertlm.Content.AudioFile(audioPath),
            com.google.ai.edge.litertlm.Content.Text(prompt.currentInput)
        )
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
                val contents = buildContents(prompt, extraContents())

                if (onToken != null) {
                    var finalResponse = ""
                    val toolCalls = mutableListOf<ModelToolCall>()
                    var error: Throwable? = null

                    try {
                        currentConversation.sendMessageAsync(contents).collect { message ->
                            yield() // CPU 점유율 양보 (UI 스레드 기아 방지)
                            collectToolCalls(message, toolCalls)
                            val token = message.contents.contents
                                .filterIsInstance<com.google.ai.edge.litertlm.Content.Text>()
                                .joinToString("") { it.text }
                            if (token.isNotEmpty()) {
                                onToken.invoke(token)
                                finalResponse += token

                                val currentTemp = metricsCollector.getCurrentTemp()
                                if (currentTemp >= 48.0f) {
                                    kotlinx.coroutines.delay(50)
                                } else if (currentTemp >= 43.0f) {
                                    kotlinx.coroutines.delay(15)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        error = e
                    }

                    metricsCollector.recordEnd(System.currentTimeMillis() - startTime)

                    if (error != null) {
                        AppResult.Failure(AppError.ModelInferenceError(error.message ?: "스트리밍 중 에러 발생"))
                    } else {
                        logTurn(finalResponse, toolCalls)
                        AppResult.Success(ModelTurn(finalResponse, toolCalls))
                    }
                } else {
                    val message = currentConversation.sendMessage(contents)
                    val toolCalls = mutableListOf<ModelToolCall>()
                    if (message != null) collectToolCalls(message, toolCalls)
                    val messageText = message?.contents?.contents
                        ?.filterIsInstance<com.google.ai.edge.litertlm.Content.Text>()
                        ?.joinToString("") { it.text }
                        .orEmpty()

                    metricsCollector.recordEnd(System.currentTimeMillis() - startTime)

                    // [WHY] 텍스트가 비어도 툴 호출만 온 턴은 정상이다 — 모델이 말 없이 곧바로
                    // 툴을 부르는 경우가 흔하다. 둘 다 비었을 때만 실패로 본다.
                    if (messageText.isNotEmpty() || toolCalls.isNotEmpty()) {
                        logTurn(messageText, toolCalls)
                        AppResult.Success(ModelTurn(messageText, toolCalls))
                    } else {
                        AppResult.Failure(AppError.ModelInferenceError("응답 생성 결과가 null입니다."))
                    }
                }
            }
        } catch (e: Exception) {
            AppResult.Failure(AppError.ModelInferenceError("$failureMessage: ${e.message}"))
        }
    }

    private fun buildContents(
        prompt: ChatPrompt,
        extras: List<com.google.ai.edge.litertlm.Content>
    ): com.google.ai.edge.litertlm.Contents {
        // [WHY] 툴 실행 결과는 사용자 발화가 아니라 전용 응답 타입으로 보내야 모델 템플릿의
        // 역할과 맞는다. 이전에는 `<tool_response>` 텍스트를 사용자 턴으로 위장해 보냈다.
        prompt.toolResponse?.let { response ->
            return com.google.ai.edge.litertlm.Contents.of(
                com.google.ai.edge.litertlm.Content.ToolResponse(response.name, response.resultJson)
            )
        }
        if (extras.isNotEmpty()) {
            return com.google.ai.edge.litertlm.Contents.of(*extras.toTypedArray())
        }
        return com.google.ai.edge.litertlm.Contents.of(
            com.google.ai.edge.litertlm.Content.Text(prompt.currentInput)
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
                args = call.arguments
            )
            if (into.none { it.name == mapped.name && it.args == mapped.args }) {
                into += mapped
            }
        }
    }

    // [WHY] 툴 호출 여부는 실기기에서만 확인할 수 있고 감사 로그에는 시스템 지시가 남지 않는다.
    // 전환 검증을 위해 런타임 로그로 남긴다.
    private fun logTurn(text: String, toolCalls: List<ModelToolCall>) {
        Log.d(
            "GemmaModelRunner",
            "turn done: textLen=${text.length}, toolCalls=${toolCalls.map { it.name }}"
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
            try {
                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(), // S25 Ultra 등 GPU Delegate 활성화로 최적화
                    visionBackend = Backend.GPU(),
                    cacheDir = context.cacheDir.absolutePath
                    // enableSpeculativeDecoding = true (Not supported in litertlm 0.13.1, MTP might be implicitly enabled by the model)
                )
                val newEngine = Engine(engineConfig)
                newEngine.initialize()
                engine = newEngine
                Log.d("GemmaModelRunner", "Engine initialized with GPU backend")
            } catch (e: Exception) {
                Log.e("GemmaModelRunner", "Failed to initialize GPU backend, falling back to CPU", e)
                val cpuConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    visionBackend = Backend.CPU()
                    // enableSpeculativeDecoding = true
                )
                val fallbackEngine = Engine(cpuConfig)
                fallbackEngine.initialize()
                engine = fallbackEngine
                Log.d("GemmaModelRunner", "Engine initialized with CPU backend")
            }
        }
    }

    private fun getOrCreateConversation(prompt: ChatPrompt): Conversation {
        val currentEngine = engine ?: throw IllegalStateException("Engine is not initialized")
        val existing = conversation
        val isSameSession = currentSessionId == prompt.sessionId
        // [WHY] 응답 스타일 설정 등으로 시스템 지시가 달라지면 동일 세션이라도 대화를 재생성해야
        // 모델이 새 지시를 본다.
        val isSameSystemInstruction = currentSystemInstruction == prompt.systemInstruction
        // [WHY] 선언된 툴이 달라지면(웹 검색 토글 등) 대화를 재생성해야 모델이 새 툴 목록을 본다.
        val isSameTools = currentEnabledTools == prompt.enabledTools
        val isTokenExceeded = existing != null && existing.getTokenCount() > 3000

        if (existing != null && isSameSession && isSameSystemInstruction && isSameTools && !isTokenExceeded) {
            return existing
        }

        existing?.close()

        val initialMessages = prompt.history.map {
            if (it.role.name == "USER") Message.user(it.content) else Message.model(it.content)
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
            // [WHY] temperature 0.8 은 사실 회상에 너무 높았다 — "비밀번호 1234"를 342/4213 으로
            // 왜곡했다. 툴 인자의 숫자·고유명사 정확성도 같은 이유로 낮은 값이 필요하다.
            samplerConfig = SamplerConfig(
                temperature = 0.3,
                topK = 40,
                topP = 0.9
            )
        )
        val newConversation = currentEngine.createConversation(config)
        conversation = newConversation
        currentSessionId = prompt.sessionId
        currentSystemInstruction = prompt.systemInstruction
        currentEnabledTools = prompt.enabledTools
        return newConversation
    }
}
