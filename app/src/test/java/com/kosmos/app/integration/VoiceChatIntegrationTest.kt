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
import com.kosmos.app.assistant.briefing.MorningBriefingGenerator
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.di.ModelModule
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelInfo
import com.kosmos.app.domain.modelrunner.ModelLoadManager
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelTurn
import com.kosmos.app.domain.tool.ImageProcessor
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.platform.launch.VoiceLaunchHandler

@HiltAndroidTest
@dagger.hilt.android.testing.UninstallModules(ModelModule::class)
@Config(application = HiltTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class VoiceChatIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

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
    val modelRunner: ModelRunner = object : ModelRunner {
        override val loadState = kotlinx.coroutines.flow.MutableStateFlow<ModelLoadState>(ModelLoadState.Ready(ModelInfo("mock", "mock", "1.0", "Q4", 0L)))
        override suspend fun warmUp() {}
        override suspend fun generate(prompt: ChatPrompt, onToken: ((String) -> Unit)?): AppResult<ModelTurn> {
            onToken?.invoke("Audio processed.")
            return AppResult.Success(ModelTurn("Audio processed."))
        }
        override suspend fun generateWithImage(prompt: ChatPrompt, imageBytes: ByteArray, onToken: ((String) -> Unit)?): AppResult<ModelTurn> {
            return AppResult.Success(ModelTurn("Audio processed."))
        }
        // [WHY] 오디오 경로는 이제 **전사 전용**이다. 답변은 전사문을 텍스트 턴으로 다시 보내
        // `generate` 가 만든다 (ADR-014). 그래서 여기서는 전사문만 돌려준다.
        override suspend fun generateWithAudio(prompt: ChatPrompt, audioPath: String, onToken: ((String) -> Unit)?): AppResult<ModelTurn> {
            return AppResult.Success(ModelTurn(TRANSCRIPT))
        }
        override suspend fun cancel() {}
        override fun close() {}
    }

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
        override val loadState = kotlinx.coroutines.flow.MutableStateFlow<ModelLoadState>(ModelLoadState.Ready(ModelInfo("mock", "mock", "1.0", "Q4", 0L)))
        override fun checkModelFile() {}
        override fun setInitializing() {}
        override fun setReady(modelInfo: ModelInfo) {}
    }

    @dagger.hilt.android.testing.BindValue
    @JvmField
    val audioRecorder: AudioRecorder = object : AudioRecorder(ApplicationProvider.getApplicationContext()) {
        override fun startRecording(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun stopRecording(): AppResult<java.io.File> = AppResult.Success(java.io.File.createTempFile("test", ".m4a", ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir))
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
            }
        )
    }

    @Test
    fun `마이크 토글 시 녹음 시작 및 종료 후 음성 메시지 전송 로직 검증`() = runBlocking {
        // Wait for ChatViewModel init (loadMessages) to finish by waiting for sessionId
        val initStartTime = System.currentTimeMillis()
        while (viewModel.uiState.value.sessionId.isEmpty() && System.currentTimeMillis() - initStartTime < 3000) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(50)
        }

        // 1. 녹음 시작 요청
        viewModel.toggleRecording()
        
        assertTrue("녹음 상태가 true여야 합니다.", viewModel.uiState.value.isRecording)

        // 2. 녹음 종료 요청
        viewModel.toggleRecording()
        
        assertTrue("녹음 상태가 false여야 합니다.", !viewModel.uiState.value.isRecording)
        assertTrue("메시지 전송이 시작되어야 합니다. 에러: ${viewModel.uiState.value.error}", viewModel.uiState.value.isInFlight)

        // 3. 메시지 전송 대기(isInFlight가 false가 될때까지)
        val startTime = System.currentTimeMillis()
        while (viewModel.uiState.value.isInFlight && System.currentTimeMillis() - startTime < 3000) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(50)
        }
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        
        val finalState = viewModel.uiState.value
        
        // Wait for DB to flush user message
        var dbMessages: List<ChatMessage> = emptyList()
        val dbStartTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - dbStartTime < 3000) {
            val res = conversationRepository.getRecentBySession(finalState.sessionId, limit = 50)
            dbMessages = if (res is AppResult.Success) res.data else emptyList()
            if (dbMessages.any { it.role == ChatMessage.Role.USER }) break
            Thread.sleep(100)
        }

        val assistantMessages = finalState.messages.filter { it.role == ChatMessage.Role.ASSISTANT }
        val userMessages = dbMessages.filter { it.role == ChatMessage.Role.USER }

        // [WHY] 예전 기대값은 `"(음성 메시지)"` 였다. 그러면 대화를 다시 열었을 때 사용자가
        // 무슨 말을 했는지 기록에 남지 않는다. 이제 전사문이 그대로 사용자 메시지가 된다.
        assertTrue(
            "전사문이 사용자 메시지로 저장돼야 한다. 현재: ${dbMessages.map { "${it.role}:${it.content}" }}",
            userMessages.any { it.content == TRANSCRIPT }
        )
        assertTrue(
            "음성 입력 타입이 유지돼야 한다",
            userMessages.any { it.inputType == InputType.VOICE }
        )
        assertTrue("Assistant response should be saved. Error: ${finalState.error}", assistantMessages.any { it.content.contains("Audio processed") })

        // [WHY] 음성 턴의 화면 말풍선은 전사 전 자리표시자("(음성 메시지)")로 뜨므로, 턴 종료 후
        // DB 의 전사문으로 교체되어야 한다 — 교체가 없으면 사용자가 화면에서 전사 결과를 확인할
        // 수 없다(2026-08-14 실기기 관측: 자리표시자가 세션 내내 남았다).
        val uiStartTime = System.currentTimeMillis()
        while (viewModel.uiState.value.messages.none {
                it.role == ChatMessage.Role.USER && it.content == TRANSCRIPT
            } && System.currentTimeMillis() - uiStartTime < 3000
        ) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(50)
        }
        assertTrue(
            "화면 말풍선이 자리표시자 대신 전사문이어야 한다. 현재: ${viewModel.uiState.value.messages.map { "${it.role}:${it.content}" }}",
            viewModel.uiState.value.messages.any {
                it.role == ChatMessage.Role.USER && it.content == TRANSCRIPT
            }
        )
    }

    private companion object {
        const val TRANSCRIPT = "내일 세 시에 치과 예약 잡아줘"
    }
}
