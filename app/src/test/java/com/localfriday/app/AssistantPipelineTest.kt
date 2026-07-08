package com.localfriday.app

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.assistant.orchestrator.AssistantOrchestrator
import com.localfriday.app.assistant.orchestrator.ChatRequest
import com.localfriday.app.domain.modelrunner.ModelLoadState
import com.localfriday.app.domain.modelrunner.ModelRunner
import com.localfriday.app.domain.modelrunner.ChatPrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Rule
import org.junit.Before
import javax.inject.Inject

class MockModelRunner : ModelRunner {
    override val loadState: StateFlow<ModelLoadState> = MutableStateFlow(
        ModelLoadState.Ready(com.localfriday.app.domain.modelrunner.ModelInfo("mock", "mock", 0L))
    )
    
    override suspend fun generate(prompt: ChatPrompt, onToken: ((String) -> Unit)?): AppResult<String> {
        val mockJsonResponse = """
            ```json
            {
              "type": "text",
              "text": "안녕하세요! 저는 Local Friday입니다."
            }
            ```
        """.trimIndent()
        
        onToken?.invoke(mockJsonResponse)
        return AppResult.Success(mockJsonResponse)
    }

    override suspend fun generateWithImage(prompt: ChatPrompt, imageBytes: ByteArray): AppResult<String> {
        return AppResult.Success("{\"type\": \"text\", \"text\": \"이미지를 확인했습니다.\"}")
    }

    override suspend fun cancel() {}
    override suspend fun warmUp() {}
    override fun close() {}
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class AssistantPipelineTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var orchestrator: AssistantOrchestrator

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun `기본 챗봇 텍스트 파이프라인 검증`() = runBlocking {
        // Given
        val request = ChatRequest(
            sessionId = "test_session_123",
            message = "안녕! 너는 누구야?"
        )
    }
}
