package com.kosmos.app.feature.settings

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kosmos.app.domain.modelrunner.ModelInfo
import com.kosmos.app.domain.modelrunner.ModelLoadManager
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.tool.ModelDownloadStatus
import com.kosmos.app.domain.usecase.DownloadModelUseCase
import com.kosmos.app.ui.theme.KosmosTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [ModelManagementScreenTest]
 * 기본 모델이 설치돼 있으면 "설치됨"과 확인을 거치는 "다시 받기"를 보인다 (0.41.10 M2, 사용자 결정 (가)).
 */
@RunWith(RobolectricTestRunner::class)
class ModelManagementScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val INFO = ModelInfo(modelId = "gemma", modelPath = "/x", modelVersion = "1", quantization = "int4", lastLoadedAt = 0L)

    private val useCase: DownloadModelUseCase = mockk(relaxed = true) {
        every { status } returns flowOf(ModelDownloadStatus.Idle)
    }

    private fun show(state: ModelLoadState) {
        val manager: ModelLoadManager = mockk(relaxed = true) {
            every { loadState } returns MutableStateFlow(state)
        }
        val viewModel = ModelManagementViewModel(useCase, manager)
        composeRule.setContent { KosmosTheme { ModelManagementScreen(viewModel = viewModel, onBack = {}) } }
        composeRule.waitForIdle()
    }

    @Test
    fun `설치돼 있으면 다시 받기는 확인 창을 거쳐야 내려받는다`() {
        show(ModelLoadState.Ready(INFO))

        composeRule.onNodeWithText("설치됨 — 다시 받을 필요는 없어요.").assertExists()
        composeRule.onNodeWithText("다시 받기").performClick()
        verify(exactly = 0) { useCase.enqueue(any()) }

        // 확인 창의 취소는 아무것도 하지 않는다.
        composeRule.onNodeWithText("취소").performClick()
        verify(exactly = 0) { useCase.enqueue(any()) }

        composeRule.onNodeWithText("다시 받기").performClick()
        composeRule.onNodeWithText("모델을 다시 받을까요?").assertExists()
        // 확인 창이 열리면 "다시 받기"가 둘(섹션 버튼·확인 버튼) — 마지막(확인 창)을 누른다.
        val buttons = composeRule.onAllNodes(hasText("다시 받기"))
        buttons[buttons.fetchSemanticsNodes().size - 1].performClick()
        verify(exactly = 1) { useCase.enqueue(any()) }
    }

    @Test
    fun `파일이 없으면 지금처럼 바로 내려받는다`() {
        show(ModelLoadState.NotFound("/x"))

        composeRule.onNodeWithText("설치됨 — 다시 받을 필요는 없어요.").assertDoesNotExist()
        composeRule.onNodeWithText("내려받기").performClick()
        verify(exactly = 1) { useCase.enqueue(any()) }
    }

    @Test
    fun `설치 판정은 파일을 찾은 뒤부터다`() {
        assertTrue(ModelLoadState.FileFound(INFO).isModelInstalled())
        assertTrue(ModelLoadState.InitializingEngine.isModelInstalled())
        assertFalse(ModelLoadState.Loading.isModelInstalled())
        assertFalse(ModelLoadState.NotFound("/x").isModelInstalled())
    }
}
