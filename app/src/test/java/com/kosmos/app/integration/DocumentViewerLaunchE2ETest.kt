package com.kosmos.app.integration

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.modelrunner.ModelInfo
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.feature.document.DocumentViewerActivity
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * [DocumentViewerLaunchE2ETest]
 * 다른 앱의 "연결 프로그램"처럼 VIEW 인텐트로 뷰어를 띄우면 문서가 보이고 **모델 준비(warmUp)는 한 번도 불리지 않는다**
 * (0.34.0 M3 — 모델 준비를 MainActivity 단위로 옮긴 D2 의 회귀 방지). 뷰어에 ModelRunner 를 주입하면 이 테스트가 잡는다.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
@UninstallModules(com.kosmos.app.di.ModelModule::class)
class DocumentViewerLaunchE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @get:Rule(order = 2)
    val temp = TemporaryFolder()

    @BindValue
    val modelRunner: ModelRunner = mockk(relaxed = true)

    @BindValue
    val tokenizer: com.kosmos.app.domain.tool.Tokenizer = object : com.kosmos.app.domain.tool.Tokenizer {
        override fun sizeInTokens(text: String): Int = text.length / 4
    }

    @BindValue
    val imageProcessor: com.kosmos.app.domain.tool.ImageProcessor = object : com.kosmos.app.domain.tool.ImageProcessor {
        override suspend fun processImage(rawBytes: ByteArray): AppResult<ByteArray> = AppResult.Success(rawBytes)
    }

    @BindValue
    val modelLoadManager: com.kosmos.app.domain.modelrunner.ModelLoadManager = object : com.kosmos.app.domain.modelrunner.ModelLoadManager {
        override val loadState: StateFlow<ModelLoadState> = MutableStateFlow(ModelLoadState.Ready(ModelInfo("mock", "mock", "1.0", "Q4", 0L)))
        override fun checkModelFile() {}
        override fun setInitializing() {}
        override fun setReady(modelInfo: ModelInfo) {}
    }

    @Before
    fun init() = hiltRule.inject()

    @Test
    fun `연결 프로그램으로 연 csv 가 보이고 모델은 켜지지 않는다`() {
        val file = File(temp.root, "가계부.csv").apply { writeText("날짜,항목,금액\n10-01,커피,4500\n") }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClass(ApplicationProvider.getApplicationContext(), DocumentViewerActivity::class.java)
            setDataAndType(Uri.fromFile(file), "text/csv")
        }

        ActivityScenario.launch<DocumentViewerActivity>(intent).use {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTextExists("커피")
            }
            composeRule.onNodeWithText("가계부.csv").assertIsDisplayed()
            composeRule.onNodeWithText("4500").assertIsDisplayed()
        }

        coVerify(exactly = 0) { modelRunner.warmUp() }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextExists(text: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
}
