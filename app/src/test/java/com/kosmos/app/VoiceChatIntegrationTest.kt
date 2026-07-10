package com.kosmos.app

import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.SavedStateHandle
import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.usecase.ResumeActionUseCase
import com.kosmos.app.domain.usecase.SendChatMessageUseCase
import com.kosmos.app.platform.share.ShareIntentHandler
import com.kosmos.app.platform.speech.AudioRecorder
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import com.kosmos.app.ui.feature.chat.ChatViewModel
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
@dagger.hilt.android.testing.UninstallModules(com.kosmos.app.app.di.ModelModule::class)
@Config(application = HiltTestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class VoiceChatIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var conversationRepository: ConversationRepository
    @Inject lateinit var sendChatMessageUseCase: SendChatMessageUseCase
    @Inject lateinit var resumeActionUseCase: ResumeActionUseCase
    @Inject lateinit var approvalCoordinator: ApprovalCoordinator
    @Inject lateinit var shareIntentHandler: ShareIntentHandler
    @Inject lateinit var runtimeMetricsCollector: RuntimeMetricsCollector
    @dagger.hilt.android.testing.BindValue
    @JvmField
    val modelRunner: ModelRunner = object : com.kosmos.app.domain.modelrunner.ModelRunner {
        override val loadState = kotlinx.coroutines.flow.MutableStateFlow<com.kosmos.app.domain.modelrunner.ModelLoadState>(com.kosmos.app.domain.modelrunner.ModelLoadState.Ready(com.kosmos.app.domain.modelrunner.ModelInfo("mock", "mock", "1.0", "Q4", 0L)))
        override suspend fun warmUp() {}
        override suspend fun generate(prompt: com.kosmos.app.domain.modelrunner.ChatPrompt, onToken: ((String) -> Unit)?): com.kosmos.app.core.common.AppResult<String> {
            onToken?.invoke("Audio processed.")
            return com.kosmos.app.core.common.AppResult.Success("Audio processed.")
        }
        override suspend fun generateWithImage(prompt: com.kosmos.app.domain.modelrunner.ChatPrompt, imageBytes: ByteArray, imageTokenBudget: Int, onToken: ((String) -> Unit)?): com.kosmos.app.core.common.AppResult<String> {
            return com.kosmos.app.core.common.AppResult.Success("Audio processed.")
        }
        override suspend fun generateWithAudio(prompt: com.kosmos.app.domain.modelrunner.ChatPrompt, audioPath: String, onToken: ((String) -> Unit)?): com.kosmos.app.core.common.AppResult<String> {
            onToken?.invoke("Audio processed.")
            return com.kosmos.app.core.common.AppResult.Success("Audio processed.")
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
    val audioRecorder: AudioRecorder = object : com.kosmos.app.platform.speech.AudioRecorder(ApplicationProvider.getApplicationContext()) {
        override fun startRecording(): Result<Unit> {
            return Result.success(Unit)
        }
        override fun stopRecording(): Result<java.io.File> {
            val file = java.io.File.createTempFile("test", ".m4a", ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir)
            return Result.success(file)
        }
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
            sendChatMessageUseCase = sendChatMessageUseCase,
            resumeActionUseCase = resumeActionUseCase,
            approvalCoordinator = approvalCoordinator,
            shareIntentHandler = shareIntentHandler,
            runtimeMetricsCollector = runtimeMetricsCollector,
            modelRunner = modelRunner,
            audioRecorder = audioRecorder
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
        var dbMessages: List<com.kosmos.app.domain.model.ChatMessage> = emptyList()
        val dbStartTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - dbStartTime < 3000) {
            val res = conversationRepository.getRecentBySession(finalState.sessionId)
            dbMessages = if (res is com.kosmos.app.core.common.AppResult.Success) res.data else emptyList()
            if (dbMessages.any { it.role == com.kosmos.app.domain.model.ChatMessage.Role.USER }) break
            Thread.sleep(100)
        }

        val assistantMessages = finalState.messages.filter { it.role == com.kosmos.app.domain.model.ChatMessage.Role.ASSISTANT }
        val userMessages = dbMessages.filter { it.role == com.kosmos.app.domain.model.ChatMessage.Role.USER }

        assertTrue("User audio message should be saved. Current msgs: ${dbMessages.map { "Role:${it.role}, Content:${it.content}, Type:${it.inputType}" }}", userMessages.any { it.content == "(음성 메시지)" })
        assertTrue("Assistant response should be saved. Error: ${finalState.error}", assistantMessages.any { it.content.contains("Audio processed") })
    }
}
