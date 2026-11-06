package com.kosmos.app.feature.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kosmos.app.ui.theme.KosmosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.kosmos.app.platform.share.SharedInput

@RunWith(RobolectricTestRunner::class)
class ChatInputBarTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `녹음 중에는 입력란이 잠기고 글자가 있어도 Send 대신 녹음 중지 버튼이 유지된다`() {
        rule.setContent {
            KosmosTheme {
                ChatInputBar(
                    isLoading = false,
                    isRecording = true,
                    sharedInput = SharedInput.Text("작성 중이던 글"),
                    onClearSharedInput = {},
                    onSend = {},
                    onMicClick = {},
                    onAttachClick = {},
                    onStopGeneration = {}
                )
            }
        }

        rule.onNodeWithText("작성 중이던 글").assertIsNotEnabled()
        rule.onNode(hasContentDescription("Send")).assertDoesNotExist()
        rule.onNode(hasContentDescription("녹음 중지")).assertIsDisplayed()
    }

    @Test
    fun `준비 중이면 입력·첨부·마이크가 잠기고 그 자리에 이유가 보인다(0·41·7 M2)`() {
        rule.setContent {
            KosmosTheme {
                ChatInputBar(
                    isLoading = false,
                    isRecording = false,
                    sharedInput = null,
                    onClearSharedInput = {},
                    onSend = {},
                    onMicClick = {},
                    onAttachClick = {},
                    preparingHint = "대화를 준비하는 중이에요…"
                )
            }
        }

        rule.onNodeWithText("대화를 준비하는 중이에요…").assertIsDisplayed()
        // 잠긴 입력란은 글자 입력 동작을 내놓지 않는다.
        rule.onNode(hasSetTextAction()).assertDoesNotExist()
        rule.onNode(hasContentDescription("Attach")).assertIsNotEnabled()
        rule.onNode(hasContentDescription("음성 입력") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun `준비가 끝나면 지금처럼 입력할 수 있다`() {
        rule.setContent {
            KosmosTheme {
                ChatInputBar(
                    isLoading = false,
                    isRecording = false,
                    sharedInput = null,
                    onClearSharedInput = {},
                    onSend = {},
                    onMicClick = {},
                    onAttachClick = {}
                )
            }
        }

        rule.onNode(hasSetTextAction()).assertIsEnabled()
        rule.onNode(hasContentDescription("Attach")).assertIsEnabled()
    }
}
