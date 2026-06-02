package com.localfriday.app.runtime.gemma

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.modelrunner.ModelLoadState
import com.localfriday.app.domain.modelrunner.ModelRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaModelRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeManager: GemmaRuntimeManager
) : ModelRunner {

    override val loadState: StateFlow<ModelLoadState> = runtimeManager.loadState

    private var llmInference: LlmInference? = null

    override suspend fun generate(
        prompt: String,
        onToken: ((String) -> Unit)?
    ): AppResult<String> = withContext(Dispatchers.Default) {
        val currentState = loadState.value
        if (currentState !is ModelLoadState.Ready) {
            return@withContext AppResult.Failure(AppError.ModelNotReady("Model is not ready"))
        }

        try {
            ensureInferenceInitialized(currentState.modelInfo.modelPath)
            
            // v0 에서는 비스트리밍 방식(generateResponse) 사용
            val response = llmInference?.generateResponse(prompt)
            if (response != null) {
                // 테스트용 onToken 콜백 호출
                onToken?.invoke(response)
                AppResult.Success(response)
            } else {
                AppResult.Failure(AppError.ModelInferenceError("응답 생성 결과가 null입니다."))
            }
        } catch (e: Exception) {
            AppResult.Failure(AppError.ModelInferenceError(e.message ?: "추론 중 알 수 없는 오류 발생"))
        }
    }

    override suspend fun generateWithImage(
        prompt: String,
        imageBytes: ByteArray
    ): AppResult<String> {
        // v0 에서는 텍스트-only Gemma 2b-it 를 사용하므로 이미지 처리는 미지원 (향후 확장)
        return AppResult.Failure(AppError.ModelInferenceError("현재 로드된 모델은 멀티모달을 지원하지 않습니다."))
    }

    override suspend fun cancel() {
        // No-op for now. LlmInference doesn't have an explicit cancel for generateResponse.
    }

    override suspend fun warmUp() {
        // No-op: LlmInference is lazy-loaded upon first use or explicitly created.
    }

    private fun ensureInferenceInitialized(modelPath: String) {
        if (llmInference == null) {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
        }
    }
}
