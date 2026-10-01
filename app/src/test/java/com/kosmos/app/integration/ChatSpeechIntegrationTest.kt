package com.kosmos.app.integration

import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.SavedStateHandle
import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.usecase.SendChatMessageUseCase
import com.kosmos.app.platform.share.ShareIntentHandler
import com.kosmos.app.platform.speech.AudioRecorder
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import com.kosmos.app.feature.chat.ChatViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

@HiltAndroidTest
@dagger.hilt.android.testing.UninstallModules(com.kosmos.app.di.ModelModule::class)
@Config(application = HiltTestApplication::class)
@RunWith(RobolectricTestRunner::class)
/**
 * [ChatSpeechIntegrationTest]
 * 답변 낭독 연동(0.29.0): 자동 낭독 켜짐/꺼짐, 녹음 시작 시 정지, 말풍선 재생 토글을 고정합니다.
 * [WHY] VoiceChatIntegrationTest 와 같은 Hilt 셋업(가짜 모델이 "Audio processed." 로 답한다)을 쓰고 낭독기만 대역으로 바꾼다.
 */
class ChatSpeechIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var conversationRepository: ConversationRepository
    @Inject lateinit var episodeRepository: com.kosmos.app.domain.memory.EpisodeRepository
    @Inject lateinit var briefingGenerator: com.kosmos.app.assistant.briefing.MorningBriefingGenerator
    @Inject lateinit var sendChatMessageUseCase: SendChatMessageUseCase
    @Inject lateinit var approvalCoordinator: ApprovalCoordinator
    @Inject lateinit var shareIntentHandler: ShareIntentHandler
    @Inject lateinit var runtimeMetricsCollector: RuntimeMetricsCollector
    @dagger.hilt.android.testing.BindValue
    @JvmField
    val modelRunner: ModelRunner = object : com.kosmos.app.domain.modelrunner.ModelRunner {
        override val loadState = kotlinx.coroutines.flow.MutableStateFlow<com.kosmos.app.domain.modelrunner.ModelLoadState>(com.kosmos.app.domain.modelrunner.ModelLoadState.Ready(com.kosmos.app.domain.modelrunner.ModelInfo("mock", "mock", "1.0", "Q4", 0L)))
        override suspend fun warmUp() {}
        override suspend fun generate(prompt: com.kosmos.app.domain.modelrunner.ChatPrompt, onToken: ((String) -> Unit)?): com.kosmos.app.core.common.AppResult<com.kosmos.app.domain.modelrunner.ModelTurn> {
            onToken?.invoke("Audio processed.")
            return com.kosmos.app.core.common.AppResult.Success(com.kosmos.app.domain.modelrunner.ModelTurn("Audio processed."))
        }
        override suspend fun generateWithImage(prompt: com.kosmos.app.domain.modelrunner.ChatPrompt, imageBytes: ByteArray, onToken: ((String) -> Unit)?): com.kosmos.app.core.common.AppResult<com.kosmos.app.domain.modelrunner.ModelTurn> {
            return com.kosmos.app.core.common.AppResult.Success(com.kosmos.app.domain.modelrunner.ModelTurn("Audio processed."))
        }
        // [WHY] 오디오 경로는 이제 **전사 전용**이다. 답변은 전사문을 텍스트 턴으로 다시 보내
        // `generate` 가 만든다 (ADR-014). 그래서 여기서는 전사문만 돌려준다.
        override suspend fun generateWithAudio(prompt: com.kosmos.app.domain.modelrunner.ChatPrompt, audioPath: String, onToken: ((String) -> Unit)?): com.kosmos.app.core.common.AppResult<com.kosmos.app.domain.modelrunner.ModelTurn> {
            return com.kosmos.app.core.common.AppResult.Success(com.kosmos.app.domain.modelrunner.ModelTurn(TRANSCRIPT))
        }
        override suspend fun cancel() {}
        override fun close() {}
    }

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
        override val loadState = kotlinx.coroutines.flow.MutableStateFlow<com.kosmos.app.domain.modelrunner.ModelLoadState>(com.kosmos.app.domain.modelrunner.ModelLoadState.Ready(com.kosmos.app.domain.modelrunner.ModelInfo("mock", "mock", "1.0", "Q4", 0L)))
        override fun checkModelFile() {}
        override fun setInitializing() {}
        override fun setReady(modelInfo: com.kosmos.app.domain.modelrunner.ModelInfo) {}
    }

    @dagger.hilt.android.testing.BindValue
    @JvmField
    val audioRecorder: AudioRecorder = object : com.kosmos.app.platform.speech.AudioRecorder(ApplicationProvider.getApplicationContext()) {
        override fun startRecording(): com.kosmos.app.core.common.AppResult<Unit> = com.kosmos.app.core.common.AppResult.Success(Unit)
        override suspend fun stopRecording(): com.kosmos.app.core.common.AppResult<java.io.File> = com.kosmos.app.core.common.AppResult.Success(java.io.File.createTempFile("test", ".m4a", ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir))
    }


    private val speaking = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val speech: com.kosmos.app.platform.speech.SpeechOutput = io.mockk.mockk(relaxed = true) {
        io.mockk.every { speakingMessageId } returns speaking
        io.mockk.coEvery { autoReadEnabled() } returns false
    }

    private lateinit var viewModel: ChatViewModel

    @Before
    fun init() {
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
            modelRunner = modelRunner,
            audioRecorder = audioRecorder,
            briefingGenerator = briefingGenerator,
            voiceLaunchHandler = com.kosmos.app.platform.launch.VoiceLaunchHandler(),
            // C′2 생성부 — 제안 흐름은 이 E2E 의 관심사가 아니다(빈 대기 목록).
            suggestionRepository = io.mockk.mockk {
                io.mockk.every { observePending() } returns kotlinx.coroutines.flow.flowOf(emptyList())
            },
            suggestionResolver = io.mockk.mockk(relaxed = true),
            // 0.29.0 생성부 추가 — 낭독은 E2E 범위 밖(자동 낭독 꺼짐).
            speechOutput = speech
        )
    }

    private fun pump(timeoutMs: Long = 3000, until: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!until() && System.currentTimeMillis() - start < timeoutMs) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(50)
        }
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    private fun sendAndWait(text: String) {
        pump { viewModel.uiState.value.sessionId.isNotEmpty() }
        viewModel.sendMessage(text)
        pump { !viewModel.uiState.value.isInFlight && viewModel.uiState.value.messages.any { it.role == com.kosmos.app.domain.model.ChatMessage.Role.ASSISTANT } }
    }

    @Test
    fun `자동 낭독이 켜져 있으면 확정된 답변을 읽는다`() {
        io.mockk.coEvery { speech.autoReadEnabled() } returns true

        sendAndWait("안녕")

        val answer = viewModel.uiState.value.messages.last { it.role == com.kosmos.app.domain.model.ChatMessage.Role.ASSISTANT }
        io.mockk.coVerify(timeout = 3000, exactly = 1) { speech.speak(answer.id, answer.content) }
    }

    @Test
    fun `자동 낭독이 꺼져 있으면 읽지 않는다`() {
        sendAndWait("안녕")

        io.mockk.coVerify(exactly = 0) { speech.speak(any(), any()) }
    }

    @Test
    fun `녹음을 시작하면 낭독을 멈춘다`() {
        pump { viewModel.uiState.value.sessionId.isNotEmpty() }

        viewModel.toggleRecording()

        io.mockk.verify(atLeast = 1) { speech.stop() }
        assertTrue(viewModel.uiState.value.isRecording)
    }

    @Test
    fun `재생 버튼은 읽던 메시지면 멈추고 아니면 그 메시지를 읽는다`() {
        val message = com.kosmos.app.domain.model.ChatMessage(
            id = "m1", sessionId = "s", role = com.kosmos.app.domain.model.ChatMessage.Role.ASSISTANT,
            content = "답변", inputType = com.kosmos.app.domain.model.InputType.TEXT, createdAt = 0L
        )

        viewModel.toggleSpeak(message)
        io.mockk.coVerify(timeout = 3000, exactly = 1) { speech.speak("m1", "답변") }

        speaking.value = "m1"
        viewModel.toggleSpeak(message)
        io.mockk.verify(atLeast = 1) { speech.stop() }
    }

    private companion object {
        const val TRANSCRIPT = "내일 세 시에 치과 예약 잡아줘"
    }
}
