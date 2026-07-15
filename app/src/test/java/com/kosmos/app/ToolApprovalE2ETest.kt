package com.kosmos.app

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
import com.kosmos.app.domain.usecase.ResumeActionUseCase
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
    }

    @dagger.hilt.android.testing.BindValue
    @JvmField
    val audioRecorder: AudioRecorder = object : com.kosmos.app.platform.speech.AudioRecorder(ApplicationProvider.getApplicationContext()) {
        override fun startRecording(): Result<Unit> = Result.success(Unit)
        override fun stopRecording(): Result<java.io.File> = Result.success(java.io.File.createTempFile("t", "a"))
    }

    @dagger.hilt.android.testing.BindValue
    @JvmField
    val mockModelRunner: ModelRunner = object : ModelRunner {
        override val loadState: StateFlow<ModelLoadState> = MutableStateFlow(ModelLoadState.Ready(ModelInfo("mock", "mock", "1.0", "Q4", 0L)))
        override suspend fun warmUp() {}
        
        override suspend fun generate(
            prompt: com.kosmos.app.domain.modelrunner.ChatPrompt,
            onToken: ((String) -> Unit)?
        ): com.kosmos.app.core.common.AppResult<String> {
            val response = if (prompt.currentInput.contains("<tool_response>")) {
                if (prompt.currentInput.contains("취소했습니다")) {
                    "알겠습니다. 일정 추가를 취소했습니다."
                } else {
                    "일정 처리가 완료되었습니다."
                }
            } else if (prompt.currentInput.contains("취소")) {
                "<tool_call>\n{\"name\":\"AddSchedule\",\"args\":{\"title\":\"약속\",\"startTime\":\"12:00\",\"endTime\":\"13:00\",\"description\":\"점심 약속\"}}\n</tool_call>"
            } else {
                "<tool_call>\n{\"name\":\"AddSchedule\",\"args\":{\"title\":\"회의\",\"startTime\":\"15:00\",\"endTime\":\"16:00\",\"description\":\"프로젝트 회의\"}}\n</tool_call>"
            }
            onToken?.invoke(response)
            return com.kosmos.app.core.common.AppResult.Success(response)
        }

        override suspend fun generateWithImage(
            prompt: com.kosmos.app.domain.modelrunner.ChatPrompt,
            imageBytes: ByteArray,
            imageTokenBudget: Int,
            onToken: ((String) -> Unit)?
        ): com.kosmos.app.core.common.AppResult<String> = generate(prompt, onToken)

        override suspend fun generateWithAudio(
            prompt: com.kosmos.app.domain.modelrunner.ChatPrompt,
            audioPath: String,
            onToken: ((String) -> Unit)?
        ): com.kosmos.app.core.common.AppResult<String> = generate(prompt, onToken)

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
        
        composeTestRule.onNode(hasSetTextAction()).performTextInput("오늘 오후 3시에 회의 일정 잡아줘")
        composeTestRule.onNodeWithText("↑").performClick()
        
        waitForIdleWithPolling(5000) {
            approvalCoordinator.pendingRequest.value != null
        }
        
        composeTestRule.onNodeWithText("일정 추가 승인").assertExists()
        
        composeTestRule.onNodeWithText("승인").performClick()
        
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
        
        composeTestRule.onNode(hasSetTextAction()).performTextInput("내일 점심 약속 잡아줘, 취소할거야")
        composeTestRule.onNodeWithText("↑").performClick()
        
        waitForIdleWithPolling(5000) {
            approvalCoordinator.pendingRequest.value != null
        }
        
        composeTestRule.onNodeWithText("일정 추가 승인").assertExists()
        
        composeTestRule.onNodeWithText("거절").performClick()
        
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
            throw AssertionError("Condition not met within $timeoutMillis ms")
        }
    }
}
