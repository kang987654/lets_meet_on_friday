package com.kosmos.app.integration

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.SavedStateHandle
import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.modelrunner.ModelInfo
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner

import com.kosmos.app.domain.usecase.SendChatMessageUseCase
import com.kosmos.app.feature.chat.ChatScreen
import com.kosmos.app.feature.chat.ChatViewModel
import com.kosmos.app.platform.share.ShareIntentHandler
import com.kosmos.app.platform.speech.AudioRecorder
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(com.kosmos.app.di.ModelModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = dagger.hilt.android.testing.HiltTestApplication::class, sdk = [34])
class ToolApprovalE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var conversationRepository: ConversationRepository
    @Inject lateinit var sendChatMessageUseCase: SendChatMessageUseCase
    @Inject lateinit var approvalCoordinator: ApprovalCoordinator
    @Inject lateinit var shareIntentHandler: ShareIntentHandler
    @Inject lateinit var runtimeMetricsCollector: RuntimeMetricsCollector
    
    @dagger.hilt.android.testing.BindValue
    @JvmField
    val tokenizer: com.kosmos.app.domain.tool.Tokenizer = object : com.kosmos.app.domain.tool.Tokenizer {
        override fun sizeInTokens(text: String): Int = text.length / 4
    }

    @dagger.hilt.android.testing.BindValue
    @JvmField
    val imageProcessor: com.kosmos.app.domain.tool.ImageProcessor = object : com.kosmos.app.domain.tool.ImageProcessor {
        override suspend fun processImage(rawBytes: ByteArray): com.kosmos.app.core.common.AppResult<ByteArray> {
            return com.kosmos.app.core.common.AppResult.Success(rawBytes)
        }
    }

    @dagger.hilt.android.testing.BindValue
    @JvmField
    val modelLoadManager: com.kosmos.app.domain.modelrunner.ModelLoadManager = object : com.kosmos.app.domain.modelrunner.ModelLoadManager {
        override val loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Ready(ModelInfo("mock", "mock", "1.0", "Q4", 0L)))
        override fun checkModelFile() {}
        override fun setInitializing() {}
        override fun setReady(modelInfo: com.kosmos.app.domain.modelrunner.ModelInfo) {}
    }

    @dagger.hilt.android.testing.BindValue
    @JvmField
    val audioRecorder: AudioRecorder = object : com.kosmos.app.platform.speech.AudioRecorder(ApplicationProvider.getApplicationContext()) {
        override fun startRecording(): com.kosmos.app.core.common.AppResult<Unit> = com.kosmos.app.core.common.AppResult.Success(Unit)
        override suspend fun stopRecording(): com.kosmos.app.core.common.AppResult<java.io.File> = com.kosmos.app.core.common.AppResult.Success(java.io.File.createTempFile("t", "a"))
    }

    @dagger.hilt.android.testing.BindValue
    @JvmField
    val mockModelRunner: ModelRunner = object : ModelRunner {
        override val loadState: StateFlow<ModelLoadState> = MutableStateFlow(ModelLoadState.Ready(ModelInfo("mock", "mock", "1.0", "Q4", 0L)))
        override suspend fun warmUp() {}
        
        // [WHY] 툴 호출을 `<tool_call>` XML 텍스트가 아니라 구조화된 ModelToolCall 로 준다 —
        // 실제 런타임이 그렇게 돌려주기 때문이다 (ADR-008). 턴 구분도 `<tool_response>` 문자열
        // 검사 대신 prompt.toolResponse 존재 여부로 한다.
        override suspend fun generate(
            prompt: com.kosmos.app.domain.modelrunner.ChatPrompt,
            onToken: ((String) -> Unit)?
        ): com.kosmos.app.core.common.AppResult<com.kosmos.app.domain.modelrunner.ModelTurn> {
            val toolResponse = prompt.toolResponse
            val turn = if (toolResponse != null) {
                val text = if (toolResponse.resultJson.contains("취소")) {
                    "알겠습니다. 일정 추가를 취소했습니다."
                } else {
                    "일정 처리가 완료되었습니다."
                }
                com.kosmos.app.domain.modelrunner.ModelTurn(text)
            } else {
                val isCancelScenario = prompt.currentInput.contains("취소")
                com.kosmos.app.domain.modelrunner.ModelTurn(
                    text = "",
                    toolCalls = listOf(
                        com.kosmos.app.domain.modelrunner.ModelToolCall(
                            name = "AddSchedule",
                            args = mapOf(
                                "title" to if (isCancelScenario) "약속" else "회의",
                                "startTime" to if (isCancelScenario) "12:00" else "15:00",
                                "endTime" to if (isCancelScenario) "13:00" else "16:00",
                                "description" to if (isCancelScenario) "점심 약속" else "프로젝트 회의"
                            )
                        )
                    )
                )
            }
            turn.text.takeIf { it.isNotEmpty() }?.let { onToken?.invoke(it) }
            return com.kosmos.app.core.common.AppResult.Success(turn)
        }

        override suspend fun generateWithImage(
            prompt: com.kosmos.app.domain.modelrunner.ChatPrompt,
            imageBytes: ByteArray,
            imageTokenBudget: Int,
            onToken: ((String) -> Unit)?
        ): com.kosmos.app.core.common.AppResult<com.kosmos.app.domain.modelrunner.ModelTurn> = generate(prompt, onToken)

        override suspend fun generateWithAudio(
            prompt: com.kosmos.app.domain.modelrunner.ChatPrompt,
            audioPath: String,
            onToken: ((String) -> Unit)?
        ): com.kosmos.app.core.common.AppResult<com.kosmos.app.domain.modelrunner.ModelTurn> = generate(prompt, onToken)

        override suspend fun cancel() {}
        override fun close() {}
    }


    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        viewModel = ChatViewModel(
            context = ApplicationProvider.getApplicationContext(),
            savedStateHandle = SavedStateHandle(),
            sessionStore = sessionStore,
            conversationRepository = conversationRepository,
            sendChatMessageUseCase = sendChatMessageUseCase,
            approvalCoordinator = approvalCoordinator,
            shareIntentHandler = shareIntentHandler,
            runtimeMetricsCollector = runtimeMetricsCollector,
            modelRunner = mockModelRunner,
            audioRecorder = audioRecorder
        )
    }

    @Test
    fun testAddSchedule_ApprovalFlow_Accept() {
        composeTestRule.setContent {
            ChatScreen(viewModel = viewModel)
        }
        
        waitForIdleWithPolling(3000) {
            viewModel.uiState.value.sessionId.isNotEmpty()
        }
        
        viewModel.sendMessage("오늘 오후 3시에 회의 일정 잡아줘")
        
        waitForIdleWithPolling(5000) {
            approvalCoordinator.pendingRequest.value != null
        }
        
        viewModel.approvePendingRequest()
        
        waitForIdleWithPolling(5000) {
            !viewModel.uiState.value.isInFlight && viewModel.uiState.value.messages.any { it.content.contains("일정 처리가 완료되었습니다.") }
        }
        
        composeTestRule.onNodeWithText("일정 처리가 완료되었습니다.").assertExists()
    }

    @Test
    fun testAddSchedule_ApprovalFlow_Reject() {
        composeTestRule.setContent {
            ChatScreen(viewModel = viewModel)
        }
        
        waitForIdleWithPolling(3000) {
            viewModel.uiState.value.sessionId.isNotEmpty()
        }
        
        viewModel.sendMessage("내일 점심 약속 잡아줘, 취소할거야")
        
        waitForIdleWithPolling(5000) {
            approvalCoordinator.pendingRequest.value != null
        }
        
        viewModel.rejectPendingRequest()
        
        waitForIdleWithPolling(5000) {
            !viewModel.uiState.value.isInFlight && viewModel.uiState.value.messages.any { it.content.contains("취소했습니다.") }
        }
        
        composeTestRule.onNodeWithText("알겠습니다. 일정 추가를 취소했습니다.").assertExists()
    }

    private fun waitForIdleWithPolling(timeoutMillis: Long, condition: () -> Boolean) {
        val startTime = System.currentTimeMillis()
        while (!condition() && (System.currentTimeMillis() - startTime) < timeoutMillis) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(100)
        }
        if (!condition()) {
            println("TIMEOUT: condition not met. pendingRequest=${approvalCoordinator.pendingRequest.value}")
            throw AssertionError("Condition not met within $timeoutMillis ms")
        }
    }
}
