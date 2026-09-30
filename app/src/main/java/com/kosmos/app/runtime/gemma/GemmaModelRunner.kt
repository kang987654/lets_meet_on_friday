package com.kosmos.app.runtime.gemma

import android.content.Context
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import android.util.Log
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.common.runCatchingCancellable
import com.kosmos.app.domain.modelrunner.ConversationResetEvent
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelToolCall
import com.kosmos.app.domain.modelrunner.ModelTurn
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import javax.inject.Inject
import javax.inject.Singleton
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
 * [WHY] 오디오는 **CPU 여야 한다** — GPU 로 주면 엔진 생성 자체가 실패한다(exp23 실측).
 * 비전은 CPU·GPU 양쪽에서 이미지를 정상 인식하므로 넘겨받은 백엔드를 그대로 쓴다(같은 실험).
 *
 * [WHY] 처음에는 gallery 의 `must be CPU for Gemma 3n` 주석을 근거로 댔는데, 그것은 **Gemma 3n**
 * 에 대한 말이고 우리 모델은 Gemma 4 다. 결과적으로 선택은 같았지만 근거를 실측으로 바꿨다
 * (`.agents/04_MODEL_EVIDENCE.md`).
 *
 * [WHY] `cacheDir` 을 CPU 폴백에도 넘긴다. 예전에는 GPU 경로만 받아서, 폴백으로 떨어진 기기는
 * 매 실행마다 커널 캐시를 다시 만들었다.
 *
 * [WHY] `maxNumTokens` 를 **명시 전달한다.** 예전에는 넘기지 않았고 그러면 런타임 기본값(4096)이
 * 되는데, 앱은 프리필 예산을 6000(슬라이더 최대 8000)으로 잡아 **용량을 넘는 프롬프트를 만들고
 * 있었다.** 넘기지 않으면 그 값을 Kotlin 쪽에서 볼 수 없어 예산과의 어긋남이 드러나지도 않는다
 * (자세한 근거는 [Constants.ENGINE_MAX_TOKENS]).
 *
 * [WHY] `maxNumImages` 도 명시한다. 우리는 한 턴에 이미지를 **정확히 한 장** 붙이므로(입력바의
 * 첨부 칩이 하나다) 1 이 사실이다. API 는 null 을 "모델/엔진 기본값 사용" 으로 문서화하지만,
 * 우리가 그 값을 볼 수 없다는 점은 `maxNumTokens` 와 같다 — 이미지 한 장이 시각 토큰 예산만큼
 * KV 를 먹으므로, 기본값이 우리 사용량보다 크면 4096 안에서 쓸 수 있는 히스토리가 조용히 줄어든다.
 * 올릴 때는 [Constants.ENGINE_MAX_TOKENS] 예산 계산을 함께 다시 해야 한다.
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
    maxNumImages = Constants.MAX_IMAGES_PER_TURN,
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
 * 1. [warmUp] 만 엔진을 초기화한다(GPU → 실패 시 CPU). 실패는 [ModelLoadState.Error] 로 내린다.
 * 2. 턴마다 뮤텍스 안에서 준비 상태를 재확인하고, [decideConversation] 판정으로 대화를 재사용/재생성
 * 3. 입력(텍스트·이미지·오디오)을 보내 스트리밍 또는 단일 추론, 무활동 감시로 행을 끊는다
 * 4. 추론 전후 발열 게이트·메트릭 기록 — 진단 로그는 [RuntimeDiagnostics], 설정 조립은 ConversationPolicy.kt
 */
@Singleton
class GemmaModelRunner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val runtimeManager: GemmaRuntimeManager,
    private val metricsCollector: RuntimeMetricsCollector,
    @LLMDispatcher private val llmDispatcher: CoroutineDispatcher
) : ModelRunner {

    companion object {
        /**
         * 생각 모드를 끕니다.
         *
         * [WHY] 근거는 **우리 실측**이다 — 같은 조건에서 생각 모드만 켜면 툴 호출이 4/4 → 2/4 로
         * 떨어졌고(exp24), 떨어지는 쪽은 일정 등록이었다(위키는 살아남는다). 툴 호출이 핵심
         * 기능이므로 끈 상태를 기본으로 둔다.
         *
         * [WHY] 예전에는 `extraContext = mapOf("enable_thinking" to false)` 맵으로 껐다. AAR
         * 0.14.0 에 `ThinkingConfig` 가 없어서 그것이 유일한 방법이었지만, **맵의 키 이름 하나에
         * 의존하는 상태**였다 — 키가 틀리거나 런타임이 무시하면 조용히 켜진 채로 돌고 우리는
         * 눈치채지 못한다. `enable_thinking=true` 조건에서도 `<|think|>` 마커가 출력에 한 번도
         * 나오지 않았기 때문에(exp24) **응답을 봐서는 켜졌는지 알 수 없다.** 0.16.0 이 타입 있는
         * `ThinkingConfig` 를 추가했으므로 컴파일러가 검증하는 형태로 옮긴다.
         *
         * [WHY] 공식 문서(capabilities/thinking)는 E2B·E4B 의 생각 모드가 시스템 턴의 `<|think|>`
         * 로 켜진다고 적는다. 실기기 렌더 프리페이스에 그 마커가 없었으므로 템플릿 기본값도 꺼져
         * 있는 것으로 보이지만, 명시로 두어 기본값에 기대지 않는다.
         */
        private val THINKING_OFF = com.google.ai.edge.litertlm.ThinkingConfig(enableThinking = false)

        // [WHY] 방출 지점(getOrCreateConversation)이 suspend 가 아니라 tryEmit 이 수신자 유무와
        // 무관하게 성공할 여유가 필요하다. 넘쳐 유실되면 catch-up 이 DB 상태로 복원한다.
        private const val RESET_EVENT_BUFFER = 16

        // [WHY] 무활동 감시의 점검 주기. 타임아웃(INFERENCE_INACTIVITY_TIMEOUT_MS)보다 충분히
        // 짧기만 하면 되고, 짧을수록 Default 디스패처를 자주 깨운다.
        private const val WATCHDOG_TICK_MS = 1_000L
    }

    override val loadState: StateFlow<ModelLoadState> = runtimeManager.loadState

    private val _conversationResets =
        MutableSharedFlow<ConversationResetEvent>(extraBufferCapacity = RESET_EVENT_BUFFER)
    override val conversationResets: SharedFlow<ConversationResetEvent> = _conversationResets

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var cachedKey: ConversationKey? = null

    // [WHY] 지금 send 중인 대화(캐시 채팅이든 oneShot 이든). cancel() 은 뮤텍스 밖에서 불리므로
    // @Volatile 로 가시성을 보장한다.
    @Volatile
    private var activeConversation: Conversation? = null

    // [WHY] llmDispatcher(limitedParallelism(1))는 suspension point에서 코루틴이 교차될 수 있어
    // 단독으로는 상호배제가 아니다. 엔진/대화 수명주기(초기화·생성·해제)는 뮤텍스로 직렬화한다.
    private val lifecycleMutex = Mutex()
    private val closeScope = CoroutineScope(SupervisorJob() + llmDispatcher)

    // [WHY] 감시는 llmDispatcher 에 둘 수 없다 — limitedParallelism(1)이라 네이티브 호출이
    // 그 스레드를 점유하면(정확히 우리가 감시하려는 상황) 감시 코루틴이 영영 실행되지 않는다.
    private val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 추론 무활동 감시 — [Constants.INFERENCE_INACTIVITY_TIMEOUT_MS] 동안 토큰이 오지 않으면
     * `cancelProcess()` 로 생성을 끊습니다.
     *
     * [WHY] **PRD EC1(추론 timeout) 의 집행이 없었다.** `ModelInferenceTimeout` 오류 타입과
     * 사용자 문구까지 준비돼 있었지만 생성하는 곳이 0곳 — 네이티브가 멈추면 lifecycleMutex 를
     * 쥔 채 영원히 매달려 이후 모든 턴(warmUp·close 포함)이 함께 막혔다.
     *
     * [WHY] `withTimeout` 이 아니라 감시 + `cancelProcess` 인 이유: 블로킹 JNI 호출은 코루틴
     * 취소에 협조하지 않아 `withTimeout` 은 그 스레드를 풀지 못한다. `cancelProcess` 는 취소
     * 버튼이 쓰는 네이티브 자체의 중단 경로라(뮤텍스도 잡지 않는다) 행 중인 생성을 실제로
     * 끊을 수 있는 유일한 수단이다.
     *
     * [WHY] 사용자 취소와의 구분: 둘 다 cancelProcess 로 끝나지만 `timedOut` 은 이 감시만
     * 세우므로, 사용자 취소는 지금처럼 부분 응답 보존으로, 타임아웃은 오류+재시도로 갈린다.
     */
    private inner class InferenceWatchdog(conversation: Conversation) {
        private val lastActivity = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
        @Volatile
        var timedOut = false
            private set
        private val job = watchdogScope.launch {
            while (true) {
                delay(WATCHDOG_TICK_MS)
                val idleMs = System.currentTimeMillis() - lastActivity.get()
                if (idleMs > Constants.INFERENCE_INACTIVITY_TIMEOUT_MS) {
                    timedOut = true
                    Log.w("GemmaModelRunner", "추론 무활동 ${idleMs}ms — cancelProcess 로 중단")
                    runCatching { conversation.cancelProcess() }
                    break
                }
            }
        }

        fun beat() = lastActivity.set(System.currentTimeMillis())
        fun stop() = job.cancel()
    }

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
            Content.ImageBytes(imageBytes),
            Content.Text(prompt.currentInput)
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
            add(Content.AudioFile(audioPath))
            if (prompt.currentInput.isNotBlank()) {
                add(Content.Text(prompt.currentInput))
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
        extraContents: () -> List<Content>
    ): AppResult<ModelTurn> = withContext(llmDispatcher) {
        if (loadState.value !is ModelLoadState.Ready) {
            return@withContext AppResult.Failure(AppError.ModelNotReady("Model is not ready"))
        }

        try {
            lifecycleMutex.withLock {
                // [WHY] 상태 검사를 뮤텍스 **안에서** 다시 한다. 밖에서 Ready 를 본 뒤 뮤텍스를
                // 기다리는 사이 close()(onStop)가 먼저 엔진을 해제할 수 있고, 예전에는 그 턴이
                // 뮤텍스를 얻자마자 엔진을 다시 로드했다 — 백그라운드에서 3.6GB 로드(AGENTS §2-⑥
                // 위반, 요약 드레인이 onStop 직후 도는 경로). 엔진 초기화는 warmUp 만 맡는다.
                val readyEngine = engine
                if (loadState.value !is ModelLoadState.Ready || readyEngine == null) {
                    return@withLock AppResult.Failure(AppError.ModelNotReady("Engine released"))
                }

                // [WHY] 임계 발열(≥48°C) 시 추론을 진행하면서 감사 로그만 남기던 문제 수정 —
                // 사전 조건 실패면 실제로 추론을 중단하고 오류를 반환한다.
                val preconditionResult = metricsCollector.checkPreconditions()
                if (preconditionResult is AppResult.Failure) {
                    return@withLock AppResult.Failure(preconditionResult.error)
                }
                metricsCollector.handleCooldownIfNecessary()
                metricsCollector.recordStart()
                val startTime = System.currentTimeMillis()

                val currentConversation = getOrCreateConversation(readyEngine, prompt)
                val outgoing = buildMessage(prompt, extraContents())
                activeConversation = currentConversation

                // [WHY] 툴 회신 턴은 재생성이 금지된 턴이라(0.8.5 가드) 어떤 예산 검사도 거치지
                // 않고 KV 에 얹힌다 — 초과가 일어난다면 바로 이 지점이다. 직전 값을 남겨야
                // 턴 종료 값과의 델타로 "툴 응답 + 생성이 실제로 몇 토큰을 먹었는지"가 나온다.
                if (prompt.toolResponse != null) RuntimeDiagnostics.logKvUsage("툴 회신 직전", currentConversation)

                val watchdog = InferenceWatchdog(currentConversation)

                // [WHY] 부수 계산용 임시 대화는 여기서 반드시 닫는다. 캐시하지 않으므로 이
                // 시점을 놓치면 네이티브 자원이 그대로 샌다.
                try {
                if (onToken != null) {
                    val finalResponse = StringBuilder()
                    val toolCalls = mutableListOf<ModelToolCall>()
                    var error: Throwable? = null
                    // [WHY] 네이티브가 토큰 하나당 메시지 하나를 보내므로, 이 수가 곧 생성 토큰
                    // 수다. 상단 상태 표시의 tok/s 가 여기서 나온다 (ADR-015).
                    var tokenCount = 0

                    try {
                        // [WHY] **토큰 유실 방지.** `sendMessageAsync` 는 callbackFlow 이고
                        // (0.14.0 바이트코드에서 확인 — 0.16.0 상향 후 재검증하지 않았으나
                        // 무한 버퍼는 사실이 바뀌어도 무해한 방어다),
                        // 네이티브 콜백(`Conversation$sendMessageAsync$1$1.onMessage`)이
                        // `ProducerScope.trySend(message)` 를 호출한 뒤 **반환값을 버린다**(바이트코드
                        // 확인). callbackFlow 의 기본 용량은 64 이고, 가득 차면 `trySend` 는 예외도
                        // 로그도 없이 실패한다 — 그 토큰은 사라진다. 수집이 조금이라도 느리면
                        // 답변 중간중간의 글자가 조용히 빠진다("2015년 10월" → "205년 10").
                        //
                        // 무한 버퍼를 끼우면 이 Flow 의 소비자는 버퍼 연산자가 되어 즉시 비워 가고,
                        // 우리 본문이 얼마나 느리든 네이티브 채널이 넘치지 않는다.
                        currentConversation.sendMessageAsync(outgoing, thinkingConfig = THINKING_OFF)
                            .buffer(kotlinx.coroutines.channels.Channel.UNLIMITED)
                            .collect { message ->
                                yield() // CPU 점유율 양보 (UI 스레드 기아 방지)
                                watchdog.beat()
                                collectToolCalls(message, toolCalls)
                                val token = message.textContent()
                                if (token.isNotEmpty()) {
                                    onToken.invoke(token)
                                    finalResponse.append(token)
                                    tokenCount++
                                }
                            }
                    } catch (e: Exception) {
                        // [WHY] **이 코루틴 자신이 취소된 경우만** 되던진다. 사용자 취소는
                        // cancelProcess 로 네이티브 스트림을 끊는 것이라(부분 응답 보존, ChatViewModel
                        // .cancelGeneration) 그 끝남이 어떤 예외로 오든 여기서 오류로 흡수해야 한다 —
                        // 반면 viewModelScope 취소 같은 진짜 코루틴 취소를 Failure 로 바꾸면 호출자가
                        // 취소된 줄 모르고 오류 말풍선을 DB 에 쓴다.
                        currentCoroutineContext().ensureActive()
                        error = e
                    }

                    metricsCollector.recordEnd(System.currentTimeMillis() - startTime, tokenCount)
                    if (!prompt.oneShot) RuntimeDiagnostics.logKvUsage("턴 종료", currentConversation)

                    // [WHY] 타임아웃 판정이 스트리밍 오류보다 먼저다 — cancelProcess 가 수집을
                    // 예외로 끝낼 수 있는데, 그 예외를 일반 추론 오류로 보고하면 "다시 시도해
                    // 주세요" 대신 원인 불명의 실패로 보인다.
                    if (watchdog.timedOut) {
                        AppResult.Failure(AppError.ModelInferenceTimeout(System.currentTimeMillis() - startTime))
                    } else if (error != null) {
                        AppResult.Failure(AppError.ModelInferenceError(error.message ?: "스트리밍 중 에러 발생"))
                    } else {
                        // [WHY] 비스트리밍과 달리 빈 결과를 실패로 바꾸지 않는다 — 사용자 취소가
                        // 첫 토큰 전에 끊으면 정상적으로 빈 부분 응답이 되고, 그 처리(빈 말풍선을
                        // 안 남김)는 BaseAgent 의 몫이다.
                        val text = finalResponse.toString()
                        RuntimeDiagnostics.logTurn(prompt, text, toolCalls)
                        AppResult.Success(ModelTurn(text, toolCalls))
                    }
                } else {
                    // [WHY] runCatching 인 이유 — 감시가 cancelProcess 를 부르면 이 블로킹 호출이
                    // 예외로 끝날 수 있고, 그것을 바깥 catch 로 흘리면 Timeout 이 아니라 일반
                    // 추론 오류로 둔갑한다. 비스트리밍은 토큰 신호가 없어 무활동 = 총시간이다.
                    val sendResult = runCatching {
                        currentConversation.sendMessage(outgoing, thinkingConfig = THINKING_OFF)
                    }
                    if (watchdog.timedOut) {
                        metricsCollector.recordEnd(System.currentTimeMillis() - startTime)
                        return@withLock AppResult.Failure(
                            AppError.ModelInferenceTimeout(System.currentTimeMillis() - startTime)
                        )
                    }
                    val message = sendResult.getOrThrow()
                    val toolCalls = mutableListOf<ModelToolCall>()
                    collectToolCalls(message, toolCalls)
                    val messageText = message.textContent()

                    metricsCollector.recordEnd(System.currentTimeMillis() - startTime)
                    if (!prompt.oneShot) RuntimeDiagnostics.logKvUsage("턴 종료", currentConversation)

                    // [WHY] 텍스트가 비어도 툴 호출만 온 턴은 정상이다 — 모델이 말 없이 곧바로
                    // 툴을 부르는 경우가 흔하다. 둘 다 비었을 때만 실패로 본다.
                    if (messageText.isNotEmpty() || toolCalls.isNotEmpty()) {
                        RuntimeDiagnostics.logTurn(prompt, messageText, toolCalls)
                        AppResult.Success(ModelTurn(messageText, toolCalls))
                    } else {
                        AppResult.Failure(AppError.ModelInferenceError("응답 생성 결과가 null입니다."))
                    }
                }
                } finally {
                    watchdog.stop()
                    activeConversation = null
                    if (prompt.oneShot) runCatching { currentConversation.close() }
                }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppResult.Failure(AppError.ModelInferenceError("$failureMessage: ${e.message}"))
        }
    }

    private fun Message.textContent(): String =
        contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }

    private fun buildMessage(
        prompt: ChatPrompt,
        extras: List<Content>
    ): Message {
        // [WHY] 툴 실행 결과는 사용자 발화가 아니라 **TOOL 역할 메시지**로 보내야 모델 템플릿의
        // 역할과 맞는다. Contents 오버로드는 무조건 Message.user 로 감싸므로(바이트코드 확인)
        // 여기서 직접 Message.tool 로 감싼다 — 공식 문서의 수동 툴 호출 예제와 같은 형태다.
        prompt.toolResponse?.let { response ->
            return Message.tool(
                Contents.of(
                    Content.ToolResponse(response.name, response.resultJson)
                )
            )
        }
        if (extras.isNotEmpty()) {
            return Message.user(Contents.of(*extras.toTypedArray()))
        }
        return Message.user(
            Contents.of(Content.Text(prompt.currentInput))
        )
    }

    /**
     * [WHY] 스트리밍 중 같은 툴 호출이 여러 메시지에 걸쳐 반복 노출될 수 있으므로 이름+인자로
     * 중복을 제거한다.
     */
    private fun collectToolCalls(
        message: Message,
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

    override suspend fun cancel() {
        // [WHY] cancelProcess는 진행 중 스트리밍을 중단시키기 위한 호출이므로 뮤텍스를 잡지 않는다
        // (생성 본문이 뮤텍스를 보유 중이어도 취소가 가능해야 함).
        // [WHY] 지금 돌고 있는 대화를 끊는다 — 예전에는 캐시된 채팅 대화만 끊어서, 진행 중인
        // oneShot(음성 전사 등 임시 대화)에는 취소가 닿지 않았다.
        runCatching { (activeConversation ?: conversation)?.cancelProcess() }
    }

    override fun close() {
        // [WHY] 메인 스레드에서 네이티브 close를 직접 부르면 진행 중 추론과 경합(use-after-free)하고
        // blocking으로 ANR 위험이 있다. 진행 중 생성에 취소를 요청한 뒤, LLM 디스패처에서
        // 뮤텍스로 직렬화하여(현재 생성 종료 후) 해제한다.
        runCatching { (activeConversation ?: conversation)?.cancelProcess() }
        closeScope.launch {
            lifecycleMutex.withLock {
                runCatching { conversation?.close() }
                conversation = null
                runCatching { engine?.close() }
                engine = null
                cachedKey = null
                // [WHY] 엔진을 해제했는데 loadState 를 Ready 로 두면 상태가 거짓이 된다 —
                // 재진입한 스플래시가 낡은 Ready 를 믿고 warmUp 을 건너뛰고, 이후 설정 화면의
                // 재탐색(checkModelFile)이 상태를 FileFound 로 되돌리면 warmUp 을 불러줄 곳이
                // 없어 "엔진 준비 중" 스피너가 영원히 멈춘다(2026-08-14 실기기 스모크).
                // 해제 직후 상태를 사실(파일은 있고 엔진은 없음 = FileFound)로 되돌린다.
                runtimeManager.checkModelFile()
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
            // [WHY] 실패를 상태로 내린다. 예전에는 GPU·CPU 가 둘 다 실패하면 예외가 호출자
            // (KosmosApp.onStart 의 lifecycleScope)로 새어 앱이 죽고, 상태는 InitializingEngine 에
            // 고정됐다 — ModelLoadState.Error 를 만드는 곳이 0곳이라 스플래시의 "다시 시도"
            // 경로가 성립하지 않았다. retry 는 checkModelFile 이 FileFound 로 되돌려 다시 탄다.
            val initialized = runCatchingCancellable {
                withContext(llmDispatcher) {
                    lifecycleMutex.withLock {
                        ensureInferenceInitialized(currentState.modelInfo.modelPath)
                    }
                }
            }
            initialized
                .onSuccess { runtimeManager.setReady(currentState.modelInfo) }
                .onFailure { e ->
                    Log.e("GemmaModelRunner", "Engine initialization failed on every backend", e)
                    runtimeManager.setError(AppError.ModelLoadFailed("엔진 초기화 실패: ${e.message}"))
                }
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
            // S25 Ultra 등 GPU Delegate 활성화로 최적화
            var gpuEngine: Engine? = null
            try {
                gpuEngine = Engine(buildEngineConfig(modelPath, Backend.GPU(), cacheDir))
                gpuEngine.initialize()
                engine = gpuEngine
                Log.d("GemmaModelRunner", "Engine initialized with GPU backend")
            } catch (e: Exception) {
                Log.e("GemmaModelRunner", "Failed to initialize GPU backend, falling back to CPU", e)
                // [WHY] 초기화에 실패한 GPU 엔진도 네이티브 핸들을 쥐고 있을 수 있다 — 버리기 전에 닫는다.
                runCatching { gpuEngine?.close() }
                val fallbackEngine = Engine(buildEngineConfig(modelPath, Backend.CPU(), cacheDir))
                fallbackEngine.initialize()
                engine = fallbackEngine
                Log.d("GemmaModelRunner", "Engine initialized with CPU backend")
            }
        }
    }

    // [WHY] ExperimentalFlags(제약 디코딩 전역 플래그)가 @ExperimentalApi 다 — 툴 호출 형식 강제에
    // 필수라 감수한다. 버전 상향마다 존재 여부를 컴파일이 검증해 준다.
    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    private fun getOrCreateConversation(currentEngine: Engine, prompt: ChatPrompt): Conversation {
        // [WHY] 부수 계산(음성 전사, 일정 요약)은 시스템 지시와 sessionId 가 채팅과 다르므로,
        // 캐시된 대화에 섞으면 재사용 판정이 깨져 채팅 전체가 다시 프리필된다(ADR-010).
        // 캐시를 **건드리지 않고** 임시 대화를 만들어 돌려준다 — 호출자가 닫는다.
        if (prompt.oneShot) return currentEngine.createConversation(oneShotConversationConfig(prompt))

        val existing = conversation
        val decision = decideConversation(cachedKey.takeIf { existing != null }, prompt) {
            val tokens = existing?.getTokenCount() ?: 0
            RuntimeDiagnostics.warnIfOverCeiling(tokens)
            tokens
        }
        if (decision is ConversationDecision.Reuse && existing != null) return existing

        // [WHY] 리셋 이벤트를 밖으로 낸다 (ADR-022 — 예산 리셋이 에피소드 경계 후보).
        // tryEmit — 이 함수는 suspend 가 아니고, 수신자가 없거나 느려도 리셋 자체를 막으면 안 된다.
        (decision as? ConversationDecision.Recreate)?.resetReason?.let { reason ->
            _conversationResets.tryEmit(ConversationResetEvent(prompt.sessionId, reason))
        }

        // [WHY] 닫기 **전에** 캐시를 비운다. 예전에는 close 뒤 createConversation 이 던지면 필드가
        // 닫힌 네이티브 객체를 계속 가리켜, 다음 턴이 getTokenCount 를 부르거나 툴 회신 경로가
        // 그 객체를 재사용했다(use-after-free).
        conversation = null
        cachedKey = null
        existing?.let { runCatching { it.close() } }

        // [WHY] 이 플래그가 툴 스키마로부터 FST 문법을 만들어 모델 출력을 호출 구문으로 강제한다.
        // 근거는 **우리 실측**이다 — 선언만으로는 4B 모델이 호출 형식을 지키지 못해 `toolCalls` 가
        // 비었다(0.8.0 실기기). 켠 상태에서 자릿수가 깨지지 않는 것도 확인했다(exp15·exp22 36/36).
        // `ConversationConfig` 필드가 아니라 생성 시점에만 읽히는 전역이다.
        ExperimentalFlags.enableConversationConstrainedDecoding = prompt.enabledTools.isNotEmpty()
        val newConversation = try {
            currentEngine.createConversation(chatConversationConfig(prompt))
        } finally {
            ExperimentalFlags.enableConversationConstrainedDecoding = false
        }
        RuntimeDiagnostics.logConversationCreated(prompt, newConversation)
        conversation = newConversation
        cachedKey = ConversationKey.of(prompt)
        return newConversation
    }
}
