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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
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

    override suspend fun generate(
        prompt: ChatPrompt,
        onToken: ((String) -> Unit)?
    ): AppResult<String> = withContext(llmDispatcher) {
        val currentState = loadState.value
        if (currentState !is ModelLoadState.Ready) {
            return@withContext AppResult.Failure(AppError.ModelNotReady("Model is not ready"))
        }

        try {
            ensureInferenceInitialized(currentState.modelInfo.modelPath)
            
            val preconditionResult = metricsCollector.checkPreconditions()
            metricsCollector.handleCooldownIfNecessary()

            metricsCollector.recordStart()
            val startTime = System.currentTimeMillis()

            val currentConversation = getOrCreateConversation(prompt)

            if (onToken != null) {
                var finalResponse = ""
                var error: Throwable? = null
                
                try {
                    currentConversation.sendMessageAsync(prompt.currentInput).collect { message ->
                        yield() // CPU 점유율 양보 (UI 스레드 기아 방지)
                        val token = message.contents.contents.filterIsInstance<com.google.ai.edge.litertlm.Content.Text>().joinToString("") { it.text }
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

                val durationMs = System.currentTimeMillis() - startTime
                metricsCollector.recordEnd(durationMs)
                
                if (error != null) {
                    AppResult.Failure(AppError.ModelInferenceError(error.message ?: "스트리밍 중 에러 발생"))
                } else {
                    AppResult.Success(finalResponse)
                }
            } else {
                val message = currentConversation.sendMessage(prompt.currentInput)
                val messageText = message?.contents?.contents?.filterIsInstance<com.google.ai.edge.litertlm.Content.Text>()?.joinToString("") { it.text }
                val durationMs = System.currentTimeMillis() - startTime
                metricsCollector.recordEnd(durationMs)

                if (!messageText.isNullOrEmpty()) {
                    AppResult.Success(messageText)
                } else {
                    AppResult.Failure(AppError.ModelInferenceError("응답 생성 결과가 null입니다."))
                }
            }
        } catch (e: Exception) {
            AppResult.Failure(AppError.ModelInferenceError(e.message ?: "추론 중 알 수 없는 오류 발생"))
        }
    }

    override suspend fun generateWithImage(
        prompt: ChatPrompt,
        imageBytes: ByteArray,
        imageTokenBudget: Int,
        onToken: ((String) -> Unit)?
    ): AppResult<String> = withContext(llmDispatcher) {
        val currentState = loadState.value
        if (currentState !is ModelLoadState.Ready) {
            return@withContext AppResult.Failure(AppError.ModelNotReady("Model is not ready"))
        }

        try {
            ensureInferenceInitialized(currentState.modelInfo.modelPath)
            
            val preconditionResult = metricsCollector.checkPreconditions()
            metricsCollector.handleCooldownIfNecessary()
            metricsCollector.recordStart()
            val startTime = System.currentTimeMillis()

            val contentList = mutableListOf<com.google.ai.edge.litertlm.Content>()
            // 토큰 예산 파라미터를 넘길 수 있는지 가정 (API 버전에 따라 다를 수 있음)
            // 만약 빌드 에러 발생 시, 단순히 ImageBytes(imageBytes)로 롤백.
            try {
                // 리플렉션 없이 일반 호출이 안되면 변경해야 함.
                contentList.add(com.google.ai.edge.litertlm.Content.ImageBytes(imageBytes) /* TODO: pass imageTokenBudget if API allows */)
            } catch (e: Exception) {
                contentList.add(com.google.ai.edge.litertlm.Content.ImageBytes(imageBytes))
            }
            contentList.add(com.google.ai.edge.litertlm.Content.Text(prompt.currentInput))
            
            val currentConversation = getOrCreateConversation(prompt)
            
            if (onToken != null) {
                var finalResponse = ""
                var error: Throwable? = null
                
                try {
                    currentConversation.sendMessageAsync(com.google.ai.edge.litertlm.Contents.of(*contentList.toTypedArray())).collect { message ->
                        yield() // CPU 점유율 양보 (UI 스레드 기아 방지)
                        val token = message.contents.contents.filterIsInstance<com.google.ai.edge.litertlm.Content.Text>().joinToString("") { it.text }
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

                val durationMs = System.currentTimeMillis() - startTime
                metricsCollector.recordEnd(durationMs)
                
                if (error != null) {
                    AppResult.Failure(AppError.ModelInferenceError(error.message ?: "스트리밍 중 에러 발생"))
                } else {
                    AppResult.Success(finalResponse)
                }
            } else {
                val message = currentConversation.sendMessage(com.google.ai.edge.litertlm.Contents.of(*contentList.toTypedArray()))
                val messageText = message?.contents?.contents?.filterIsInstance<com.google.ai.edge.litertlm.Content.Text>()?.joinToString("") { it.text }
                
                val durationMs = System.currentTimeMillis() - startTime
                metricsCollector.recordEnd(durationMs)
                
                if (!messageText.isNullOrEmpty()) {
                    AppResult.Success(messageText)
                } else {
                    AppResult.Failure(AppError.ModelInferenceError("응답 생성 결과가 null입니다."))
                }
            }
        } catch (e: Exception) {
            AppResult.Failure(AppError.ModelInferenceError("멀티모달 추론 중 오류 발생: ${e.message}"))
        }
    }

    override suspend fun generateWithAudio(
        prompt: ChatPrompt,
        audioPath: String,
        onToken: ((String) -> Unit)?
    ): AppResult<String> = withContext(llmDispatcher) {
        val currentState = loadState.value
        if (currentState !is ModelLoadState.Ready) {
            return@withContext AppResult.Failure(AppError.ModelNotReady("Model is not ready"))
        }

        try {
            ensureInferenceInitialized(currentState.modelInfo.modelPath)
            
            val preconditionResult = metricsCollector.checkPreconditions()
            metricsCollector.handleCooldownIfNecessary()
            metricsCollector.recordStart()
            val startTime = System.currentTimeMillis()

            val contentList = mutableListOf<com.google.ai.edge.litertlm.Content>()
            contentList.add(com.google.ai.edge.litertlm.Content.AudioFile(audioPath))
            contentList.add(com.google.ai.edge.litertlm.Content.Text(prompt.currentInput))
            
            val currentConversation = getOrCreateConversation(prompt)
            
            if (onToken != null) {
                var finalResponse = ""
                var error: Throwable? = null
                
                try {
                    currentConversation.sendMessageAsync(com.google.ai.edge.litertlm.Contents.of(*contentList.toTypedArray())).collect { message ->
                        yield()
                        val token = message.contents.contents.filterIsInstance<com.google.ai.edge.litertlm.Content.Text>().joinToString("") { it.text }
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

                val durationMs = System.currentTimeMillis() - startTime
                metricsCollector.recordEnd(durationMs)
                
                if (error != null) {
                    AppResult.Failure(AppError.ModelInferenceError(error.message ?: "음성 스트리밍 중 에러 발생"))
                } else {
                    AppResult.Success(finalResponse)
                }
            } else {
                val message = currentConversation.sendMessage(com.google.ai.edge.litertlm.Contents.of(*contentList.toTypedArray()))
                val messageText = message?.contents?.contents?.filterIsInstance<com.google.ai.edge.litertlm.Content.Text>()?.joinToString("") { it.text }
                
                val durationMs = System.currentTimeMillis() - startTime
                metricsCollector.recordEnd(durationMs)
                
                if (!messageText.isNullOrEmpty()) {
                    AppResult.Success(messageText)
                } else {
                    AppResult.Failure(AppError.ModelInferenceError("음성 응답 생성 결과가 null입니다."))
                }
            }
        } catch (e: Exception) {
            AppResult.Failure(AppError.ModelInferenceError("음성 추론 중 오류 발생: ${e.message}"))
        }
    }

    override suspend fun cancel() {
        conversation?.cancelProcess()
    }



    override fun close() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        currentSessionId = null
    }

    override suspend fun warmUp() {
        val currentState = runtimeManager.loadState.value
        if (currentState is ModelLoadState.Ready) {
            runtimeManager.setInitializing()
            withContext(Dispatchers.IO) {
                ensureInferenceInitialized(currentState.modelInfo.modelPath)
            }
            runtimeManager.checkModelFile() // restores to Ready after load
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
        val isSameSession = currentSessionId == prompt.sessionId
        val isTokenExceeded = conversation != null && conversation!!.getTokenCount() > 3000

        if (conversation == null || !isSameSession || isTokenExceeded) {
            conversation?.close()
            
            val initialMessages = prompt.history.map {
                if (it.role.name == "USER") Message.user(it.content) else Message.model(it.content)
            }
            
            val config = ConversationConfig(
                systemInstruction = Contents.of(prompt.systemInstruction),
                initialMessages = initialMessages,
                samplerConfig = SamplerConfig(
                    temperature = 0.8,
                    topK = 40,
                    topP = 0.9
                )
            )
            conversation = currentEngine.createConversation(config)
            currentSessionId = prompt.sessionId
        }
        
        return conversation!!
    }
}
