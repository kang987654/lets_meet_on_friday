package com.kosmos.app.integration

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import com.kosmos.app.assistant.briefing.MorningBriefingGenerator
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.di.ModelModule
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelLoadManager
import com.kosmos.app.domain.modelrunner.ModelToolCall
import com.kosmos.app.domain.modelrunner.ModelTurn
import com.kosmos.app.domain.tool.ImageProcessor
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.platform.launch.VoiceLaunchHandler

@HiltAndroidTest
@UninstallModules(ModelModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = dagger.hilt.android.testing.HiltTestApplication::class)
class ToolApprovalE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var conversationRepository: ConversationRepository
    @Inject lateinit var episodeRepository: EpisodeRepository
    @Inject lateinit var briefingGenerator: MorningBriefingGenerator
    @Inject lateinit var sendChatMessageUseCase: SendChatMessageUseCase
    @Inject lateinit var approvalCoordinator: ApprovalCoordinator
    @Inject lateinit var shareIntentHandler: ShareIntentHandler
    @Inject lateinit var runtimeMetricsCollector: RuntimeMetricsCollector
    
    @dagger.hilt.android.testing.BindValue
    @JvmField
    val tokenizer: Tokenizer = object : Tokenizer {
        override fun sizeInTokens(text: String): Int = text.length / 4
    }

    @dagger.hilt.android.testing.BindValue
    @JvmField
    val imageProcessor: ImageProcessor = object : ImageProcessor {
        override suspend fun processImage(rawBytes: ByteArray): AppResult<ByteArray> {
            return AppResult.Success(rawBytes)
        }
    }

    @dagger.hilt.android.testing.BindValue
    @JvmField
    val modelLoadManager: ModelLoadManager = object : ModelLoadManager {
        override val loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Ready(ModelInfo("mock", "mock", "1.0", "Q4", 0L)))
        override fun checkModelFile() {}
        override fun setInitializing() {}
        override fun setReady(modelInfo: ModelInfo) {}
    }

    @dagger.hilt.android.testing.BindValue
    @JvmField
    val audioRecorder: AudioRecorder = object : AudioRecorder(ApplicationProvider.getApplicationContext()) {
        override fun startRecording(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun stopRecording(): AppResult<java.io.File> = AppResult.Success(java.io.File.createTempFile("t", "a"))
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
            prompt: ChatPrompt,
            onToken: ((String) -> Unit)?
        ): AppResult<ModelTurn> {
            val toolResponse = prompt.toolResponse
            val turn = if (toolResponse != null) {
                // 거절은 모델을 다시 부르지 않는다(0.38.6 M4) — 회신 턴은 승인된 경우뿐이다.
                ModelTurn("일정 처리가 완료되었습니다.")
            } else {
                val isCancelScenario = prompt.currentInput.contains("취소")
                ModelTurn(
                    text = "",
                    toolCalls = listOf(
                        ModelToolCall(
                            name = "AddSchedule",
                            // [WHY] 시각 값은 유효한 ISO 8601 이어야 한다 — 0.15.0 부터 인자
                            // 형식 검증이 승인 카드 전에 걸러서, 예전 픽스처("15:00")로는
                            // 승인 흐름 자체에 도달하지 못한다.
                            args = mapOf(
                                "title" to if (isCancelScenario) "약속" else "회의",
                                "startTime" to if (isCancelScenario) "2026-08-18T12:00:00" else "2026-08-17T15:00:00",
                                "endTime" to if (isCancelScenario) "2026-08-18T13:00:00" else "2026-08-17T16:00:00",
                                "description" to if (isCancelScenario) "점심 약속" else "프로젝트 회의"
                            )
                        )
                    )
                )
            }
            turn.text.takeIf { it.isNotEmpty() }?.let { onToken?.invoke(it) }
            return AppResult.Success(turn)
        }

        override suspend fun generateWithImage(
            prompt: ChatPrompt,
            imageBytes: ByteArray,
            onToken: ((String) -> Unit)?
        ): AppResult<ModelTurn> = generate(prompt, onToken)

        override suspend fun generateWithAudio(
            prompt: ChatPrompt,
            audioPath: String,
            onToken: ((String) -> Unit)?
        ): AppResult<ModelTurn> = generate(prompt, onToken)

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
            episodeRepository = episodeRepository,
            sendChatMessageUseCase = sendChatMessageUseCase,
            approvalCoordinator = approvalCoordinator,
            shareIntentHandler = shareIntentHandler,
            runtimeMetricsCollector = runtimeMetricsCollector,
            modelRunner = mockModelRunner,
            audioRecorder = audioRecorder,
            briefingGenerator = briefingGenerator,
            voiceLaunchHandler = VoiceLaunchHandler(),
            // C′2 생성부 — 제안 흐름은 이 E2E 의 관심사가 아니다(빈 대기 목록).
            suggestionRepository = io.mockk.mockk {
                io.mockk.every { observePending() } returns kotlinx.coroutines.flow.flowOf(emptyList())
            },
            suggestionResolver = io.mockk.mockk(relaxed = true),
            // 0.29.0 생성부 추가 — 낭독은 E2E 범위 밖(자동 낭독 꺼짐).
            speechOutput = io.mockk.mockk(relaxed = true) {
                io.mockk.every { speakingMessageId } returns kotlinx.coroutines.flow.MutableStateFlow(null)
                io.mockk.coEvery { autoReadEnabled() } returns false
            },
            conversationPrewarmer = io.mockk.mockk(relaxed = true)
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
            !viewModel.uiState.value.isInFlight && viewModel.uiState.value.messages.any { it.content.contains("일정을 추가하지 않았어요.") }
        }
        
        composeTestRule.onNodeWithText("일정을 추가하지 않았어요.").assertExists()
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
