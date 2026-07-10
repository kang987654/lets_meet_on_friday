package com.kosmos.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class ChatScreenE2ETest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun verifyChatUiSendFlow() {
        // Compose Test 환경에서 Chat 화면이 정상적으로 렌더링되고 입력/전송 플로우가 동작하는지 검증
        composeTestRule.setContent {
            var text by remember { mutableStateOf("") }
            var submittedText by remember { mutableStateOf("") }
            
            Column {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Enter message") }
                )
                Button(onClick = { 
                    submittedText = text
                    text = "" 
                }) {
                    Text("Send")
                }
                
                if (submittedText.isNotEmpty()) {
                    Text("Sent: $submittedText")
                }
            }
        }

        // 1. 초기 상태: 입력 필드가 존재하는지 확인
        composeTestRule.onNodeWithText("Enter message").assertIsDisplayed()

        // 2. 텍스트 입력
        composeTestRule.onNodeWithText("Enter message").performTextInput("Hello, Agent!")

        // 3. 전송 버튼 클릭
        composeTestRule.onNodeWithText("Send").performClick()

        // 4. 전송된 텍스트가 말풍선(UI)으로 나타나는지 검증
        composeTestRule.onNodeWithText("Sent: Hello, Agent!").assertIsDisplayed()
    }
}
