package com.kosmos.app.feature.cleanup

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kosmos.app.assistant.cleanup.MemoryCleanupRunner
import com.kosmos.app.domain.cleanup.MergeProposal
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.ui.theme.KosmosTheme
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [MemoryCleanupScreenTest]
 * 병합 제안은 체크 해제로 시작하고(exp42 M0 결정), 체크한 것만 적용된다 (0.30.0 M3).
 */
@RunWith(RobolectricTestRunner::class)
class MemoryCleanupScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun note(id: String, content: String) = KnowledgeNote(id = id, content = content, createdAt = 0L, updatedAt = 0L)

    private val wifi = MergeProposal(listOf(note("a", "와이파이 비밀번호는 kosmos123"), note("b", "집 와이파이 비번 kosmos123")), "집 와이파이 비밀번호는 kosmos123이다.")
    private val bike = MergeProposal(listOf(note("c", "자전거 자물쇠 번호 4821"), note("d", "자전거 자물쇠 비밀번호는 4821")), "자전거 자물쇠 비밀번호는 4821이다.")

    private val state = MutableStateFlow<MemoryCleanupRunner.State>(
        MemoryCleanupRunner.State.Review(note("weekly-review-2026-W40", "이번 주는 치과와 러닝 준비에 시간을 썼어요."), listOf(wifi, bike))
    )
    private val runner: MemoryCleanupRunner = mockk(relaxed = true) {
        every { this@mockk.state } returns this@MemoryCleanupScreenTest.state
    }
    private val viewModel = MemoryCleanupViewModel(runner)

    private fun show() {
        composeRule.setContent { KosmosTheme { MemoryCleanupScreen(viewModel = viewModel, onBack = {}) } }
        composeRule.waitForIdle()
    }

    @Test
    fun `제안은 모두 체크 해제로 시작하고 버튼은 합치지 않고 마치기다`() {
        show()

        val boxes = composeRule.onAllNodes(isToggleable())
        boxes.fetchSemanticsNodes().indices.forEach { boxes[it].assertIsOff() }
        composeRule.onNodeWithText("합치지 않고 마치기").performScrollTo()
    }

    @Test
    fun `체크한 제안만 적용한다`() {
        show()

        composeRule.onNodeWithText("→ 자전거 자물쇠 비밀번호는 4821이다.").performScrollTo().performClick()
        composeRule.onNodeWithText("선택한 1건 합치기").performScrollTo().performClick()
        composeRule.waitForIdle()

        coVerify(exactly = 1) { runner.apply(listOf(bike)) }
    }
}
