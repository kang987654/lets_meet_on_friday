package com.localfriday.app.runtime.gemma

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.modelrunner.ModelInfo
import com.localfriday.app.domain.modelrunner.ModelLoadState
import com.localfriday.app.domain.modelrunner.ModelRunner
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

    // 테스트 환경에서 응답을 조작할 수 있도록 var로 열어둡니다.
    var fakeResponse: String = """{"action":"text","content":"테스트 응답입니다."}"""

    override suspend fun generate(
        prompt: String,
        onToken: ((String) -> Unit)?
    ): AppResult<String> {
        delay(100) // 약간의 지연으로 비동기 동작 시뮬레이션
        // 테스트용이므로 onToken은 생략하고 즉시 전체 응답 반환
        onToken?.invoke(fakeResponse)
        return AppResult.Success(fakeResponse)
    }

    override suspend fun generateWithImage(
        prompt: String,
        imageBytes: ByteArray
    ): AppResult<String> {
        delay(100)
        return AppResult.Success(fakeResponse)
    }

    override suspend fun cancel() {
        // No-op for fake
    }

    override suspend fun warmUp() {
        // No-op for fake
    }
}
