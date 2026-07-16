package com.kosmos.app.integration

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.assistant.orchestrator.AssistantOrchestrator
import com.kosmos.app.assistant.orchestrator.ChatRequest
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ChatPrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Rule
import org.junit.Before
import javax.inject.Inject
import dagger.hilt.android.testing.HiltTestApplication
import org.robolectric.annotation.Config

class MockModelRunner : ModelRunner {
    override val loadState: StateFlow<ModelLoadState> = MutableStateFlow(
        ModelLoadState.Ready(com.kosmos.app.domain.modelrunner.ModelInfo("mock", "mock", "1.0", "int8", 0L))
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

    override suspend fun generateWithImage(
        prompt: ChatPrompt,
        imageBytes: ByteArray,
        imageTokenBudget: Int,
        onToken: ((String) -> Unit)?
    ): AppResult<String> {
        delay(100)
        onToken?.invoke("Image processed.")
        return AppResult.Success("Image processed.")
    }

    override suspend fun generateWithAudio(
        prompt: ChatPrompt,
        audioPath: String,
        onToken: ((String) -> Unit)?
    ): AppResult<String> {
        delay(100)
        onToken?.invoke("Audio processed.")
        return AppResult.Success("Audio processed.")
    }

    override suspend fun cancel() {}
    override suspend fun warmUp() {}
    override fun close() {}
}


@HiltAndroidTest
@Config(application = HiltTestApplication::class)
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
