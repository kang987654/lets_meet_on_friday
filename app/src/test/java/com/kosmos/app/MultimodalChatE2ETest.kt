package com.kosmos.app

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowContentResolver
import javax.inject.Inject
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull

import com.kosmos.app.ui.feature.chat.ChatScreen
import com.kosmos.app.ui.feature.chat.ChatViewModel
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.modelrunner.ModelInfo

import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.usecase.ResumeActionUseCase
import com.kosmos.app.domain.usecase.SendChatMessageUseCase
import com.kosmos.app.platform.share.ShareIntentHandler
import com.kosmos.app.platform.speech.AudioRecorder
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import androidx.lifecycle.SavedStateHandle

class FakeE2EModelRunner : ModelRunner {
    override val loadState: StateFlow<ModelLoadState> = MutableStateFlow(
        ModelLoadState.Ready(ModelInfo("test", "test", "1.0", "int8", 0L))
    )
    
    var lastPrompt: ChatPrompt? = null
    var lastImageBytes: ByteArray? = null
    var lastAudioPath: String? = null
    var generateCallCount = 0

    override suspend fun warmUp() {}
    
    override suspend fun generate(prompt: ChatPrompt, onToken: ((String) -> Unit)?): AppResult<String> {
        lastPrompt = prompt
        generateCallCount++
        onToken?.invoke("Fake response")
        return AppResult.Success("Fake response")
    }
    
    override suspend fun generateWithImage(prompt: ChatPrompt, imageBytes: ByteArray, imageTokenBudget: Int, onToken: ((String) -> Unit)?): AppResult<String> {
        lastPrompt = prompt
        lastImageBytes = imageBytes
        generateCallCount++
        onToken?.invoke("Fake image response")
        return AppResult.Success("Fake image response")
    }
    
    override suspend fun generateWithAudio(prompt: ChatPrompt, audioPath: String, onToken: ((String) -> Unit)?): AppResult<String> {
        lastPrompt = prompt
        lastAudioPath = audioPath
        generateCallCount++
        onToken?.invoke("Fake audio response")
        return AppResult.Success("Fake audio response")
    }

    override suspend fun cancel() {}
    
    override fun close() {}
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [33], instrumentedPackages = ["androidx.loader.content"])
@UninstallModules(com.kosmos.app.app.di.ModelModule::class)
class MultimodalChatE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    @BindValue
    val fakeModelRunner: ModelRunner = FakeE2EModelRunner()

    @BindValue
    val tokenizer: com.kosmos.app.domain.tool.Tokenizer = object : com.kosmos.app.domain.tool.Tokenizer {
        override fun sizeInTokens(text: String): Int = text.length / 4
    }

    @BindValue
    val audioRecorder: AudioRecorder = object : com.kosmos.app.platform.speech.AudioRecorder(ApplicationProvider.getApplicationContext()) {
        override fun startRecording(): Result<Unit> = Result.success(Unit)
        override fun stopRecording(): Result<java.io.File> = Result.success(java.io.File.createTempFile("t", "a"))
    }

    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var conversationRepository: ConversationRepository
    @Inject lateinit var sendChatMessageUseCase: SendChatMessageUseCase
    @Inject lateinit var resumeActionUseCase: ResumeActionUseCase
    @Inject lateinit var approvalCoordinator: ApprovalCoordinator
    @Inject lateinit var shareIntentHandler: ShareIntentHandler
    @Inject lateinit var runtimeMetricsCollector: RuntimeMetricsCollector

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
            modelRunner = fakeModelRunner,
            audioRecorder = audioRecorder
        )
    }

    @Test
    fun verifyDocumentAttachmentAndModelDispatchFlow() {
        val testUri = Uri.parse("content://fake/doc.txt")
        val fakeDocContent = "This is a fake PDF or Text document content."
        
        // Setup ShadowContentResolver to return our fake file bytes
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowContentResolver = Shadows.shadowOf(context.contentResolver)
        shadowContentResolver.registerInputStream(testUri, ByteArrayInputStream(fakeDocContent.toByteArray()))

        val testRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                if (contract is androidx.activity.result.contract.ActivityResultContracts.GetContent) {
                    // Simulate picking a file
                    dispatchResult(requestCode, testUri as O)
                }
            }
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = testRegistry
            }) {
                ChatScreen(viewModel = viewModel)
            }
        }

        composeTestRule.waitForIdle()

        // 1. Click attach button ("+")
        composeTestRule.onNodeWithText("+").performClick()
        
        composeTestRule.waitForIdle()

        // 2. Verify Document is attached on UI
        composeTestRule.onNodeWithText("Document Attached").assertIsDisplayed()

        // 3. Type a message
        composeTestRule.onNode(androidx.compose.ui.test.hasSetTextAction())
            .performTextInput("Please summarize this document.")

        // 4. Send message ("↑")
        composeTestRule.onNodeWithText("↑").performClick()

        // 5. Wait for the model runner to receive the prompt with the document content injected
        val runner = fakeModelRunner as FakeE2EModelRunner
        val startTime = System.currentTimeMillis()
        while (runner.lastPrompt == null && System.currentTimeMillis() - startTime < 5000) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(100)
        }
        
        assertNotNull("ModelRunner did not receive a prompt within 5 seconds", runner.lastPrompt)
        assertTrue("ModelRunner should have received the prompt", runner.generateCallCount > 0)
        
        // Orchestrator concatenates the document text into the context/history, not currentInput
        val prompt = runner.lastPrompt!!
        val historyText = prompt.history.joinToString { it.content }
        assertTrue("History or System Instruction must contain the document content", 
            historyText.contains(fakeDocContent) || prompt.systemInstruction.contains(fakeDocContent))
        assertTrue("Current input must contain user message", prompt.currentInput.contains("Please summarize this document."))
    }
}
