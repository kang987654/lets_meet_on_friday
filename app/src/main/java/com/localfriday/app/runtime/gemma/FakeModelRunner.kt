package com.localfriday.app.runtime.gemma

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.modelrunner.ModelInfo
import com.localfriday.app.domain.modelrunner.ModelLoadState
import com.localfriday.app.domain.modelrunner.ModelRunner
import com.localfriday.app.domain.modelrunner.ChatPrompt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeModelRunner : ModelRunner {
    private val _loadState = MutableStateFlow<ModelLoadState>(
        ModelLoadState.Ready(
            ModelInfo(
                modelId = "fake-model",
                modelPath = "/fake/path",
                modelVersion = "1.0",
                quantization = "INT4",
                lastLoadedAt = System.currentTimeMillis()
            )
        )
    )
    override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    // Fake API로 무조건 텍스트 뱉기
    var fakeResponse: String = """{"action":"text","content":"테스트 응답입니다."}"""

    override suspend fun generate(
        prompt: ChatPrompt,
        onToken: ((String) -> Unit)?
    ): AppResult<String> {
        delay(500)
        
        if (onToken != null) {
            val response = "안녕하세요. 저는 Fake Model입니다. 전달받은 메시지: ${prompt.currentInput}"
            val chunks = response.chunked(2)
            for (chunk in chunks) {
                delay(50)
                onToken(chunk)
            }
            return AppResult.Success(response)
        }
        
        return AppResult.Success("안녕하세요. 저는 Fake Model입니다. (Non-streaming) 전달받은 메시지: ${prompt.currentInput}")
    }

    override suspend fun generateWithImage(
        prompt: ChatPrompt,
        imageBytes: ByteArray,
        onToken: ((String) -> Unit)?
    ): AppResult<String> {
        delay(1500)
        onToken?.invoke("Fake multimodal response")
        return AppResult.Success("Fake multimodal response")
    }

    override suspend fun generateWithAudio(
        prompt: ChatPrompt,
        audioPath: String,
        onToken: ((String) -> Unit)?
    ): AppResult<String> {
        delay(1500)
        onToken?.invoke("Fake audio response")
        return AppResult.Success("Fake audio response")
    }

    override suspend fun cancel() {
        // do nothing
    }

    override suspend fun warmUp() {
        // do nothing
    }

    override fun close() {
        // do nothing
    }
}
