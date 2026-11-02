package com.kosmos.app.feature.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
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
}
