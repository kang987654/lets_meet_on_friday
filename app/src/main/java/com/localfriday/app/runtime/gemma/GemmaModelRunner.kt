package com.localfriday.app.runtime.gemma

import android.content.Context
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.modelrunner.ModelLoadState
import com.localfriday.app.domain.modelrunner.ModelRunner
import com.localfriday.app.domain.modelrunner.ChatPrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import com.localfriday.app.di.LLMDispatcher

import com.localfriday.app.runtime.metrics.RuntimeMetricsCollector

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
        imageBytes: ByteArray
    ): AppResult<String> {
        return AppResult.Failure(AppError.ModelInferenceError("현재 로드된 모델은 멀티모달을 지원하지 않습니다."))
    }

    override suspend fun cancel() {
        conversation?.cancelProcess()
    }

    override suspend fun warmUp() {
    }

    override fun close() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        currentSessionId = null
    }

    private fun ensureInferenceInitialized(modelPath: String) {
        if (engine == null) {
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU() // S25 Ultra 등 GPU Delegate 활성화로 최적화
            )
            val newEngine = Engine(engineConfig)
            newEngine.initialize()
            engine = newEngine
        }
    }

    private fun getOrCreateConversation(prompt: ChatPrompt): Conversation {
        val currentEngine = engine ?: throw IllegalStateException("Engine is not initialized")
        val isSameSession = currentSessionId == prompt.sessionId
        val isTokenExceeded = conversation != null && conversation!!.getTokenCount() > 3500

        if (conversation == null || !isSameSession || isTokenExceeded) {
            conversation?.close()
            
            val initialMessages = prompt.history.map {
                if (it.role.name == "USER") Message.user(it.content) else Message.model(it.content)
            }
            
            val config = ConversationConfig(
                systemInstruction = Contents.of(prompt.systemInstruction),
                initialMessages = initialMessages
            )
            conversation = currentEngine.createConversation(config)
            currentSessionId = prompt.sessionId
        }
        
        return conversation!!
    }
}
