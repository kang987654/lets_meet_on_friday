package com.kosmos.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.R
import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.feature.chat.EmptyChat
import com.kosmos.app.feature.settings.DataResetViewModel
import com.kosmos.app.feature.settings.SettingsScreen
import com.kosmos.app.feature.settings.SettingsViewModel
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.ThemeViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [EnglishModeUiTest]
 * 영어 로케일(`qualifiers = "en"`) 및 `KosmosTheme(language = AppLanguage.EN)` 전환 시
 * 안드로이드 표준 리소스(`values-en/strings.xml`)와 주요 화면(빈 채팅·설정 언어 토글)이
 * 영어로 렌더링되는지 검증합니다 (0.40.0 M3).
 */
@RunWith(RobolectricTestRunner::class)
class EnglishModeUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    @Config(qualifiers = "en")
    fun `영어 로케일(values-en)에서 표준 문자열 리소스가 영어로 조회된다`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Settings", context.getString(R.string.settings_title))
        assertEquals("Cancel", context.getString(R.string.common_cancel))
        assertEquals("Save", context.getString(R.string.common_save))
        assertEquals("Message…", context.getString(R.string.chat_input_hint))
    }

    @Test
    fun `기본 로케일(ko-rKR)에서는 한국어 문자열 리소스가 조회된다`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("설정", context.getString(R.string.settings_title))
        assertEquals("메시지를 입력하세요…", context.getString(R.string.chat_input_hint))
    }

    @Test
    @Config(qualifiers = "en")
    fun `영어 모드에서 빈 채팅 화면과 설정 화면의 언어 세그먼트가 영어로 표시되고 한영 전환이 동작한다`() {
        val settingsDataStore = mockk<com.kosmos.app.data.local.prefs.SettingsDataStore>(relaxed = true) {
            every { themeModeFlow } returns flowOf("SYSTEM")
            every { fontModeFlow } returns flowOf("APP")
            every { chatTextSizeFlow } returns flowOf("DEFAULT")
            every { splashStyleFlow } returns flowOf("PLANET")
            every { responseStyleFlow } returns flowOf("DEFAULT")
            every { maxTokensFlow } returns flowOf(2048)
            every { briefingEnabledFlow } returns flowOf(false)
            every { briefingTimeMinutesFlow } returns flowOf(480)
            every { autoExtractEnabledFlow } returns flowOf(true)
            every { ttsAutoReadFlow } returns flowOf(false)
            every { ttsEngineFlow } returns flowOf("")
            every { appLanguageOptionFlow } returns flowOf("EN")
        }
        val runtimeManager = mockk<com.kosmos.app.runtime.gemma.GemmaRuntimeManager>(relaxed = true) {
            every { loadState } returns kotlinx.coroutines.flow.MutableStateFlow(
                com.kosmos.app.domain.modelrunner.ModelLoadState.NotFound("/m")
            )
        }
        val speechOutput = mockk<com.kosmos.app.platform.speech.SpeechOutput>(relaxed = true) {
            every { voiceStatus } returns kotlinx.coroutines.flow.MutableStateFlow(
                com.kosmos.app.platform.speech.SpeechOutput.VoiceStatus.READY
            )
        }
        val settingsVm = SettingsViewModel(
            settingsDataStore = settingsDataStore,
            runtimeManager = runtimeManager,
            modelRunner = mockk(relaxed = true),
            briefingScheduler = mockk(relaxed = true),
            speechOutput = speechOutput
        )
        val themeVm = ThemeViewModel(settingsDataStore)
        themeVm.setLanguageOption(AppLanguage.Option.EN)
        val resetVm = DataResetViewModel(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true)
        )

        rule.setContent {
            KosmosTheme(language = AppLanguage.EN) {
                SettingsScreen(
                    viewModel = settingsVm,
                    themeViewModel = themeVm,
                    dataResetViewModel = resetVm
                )
            }
        }

        rule.onNodeWithText("Settings").assertIsDisplayed()
        rule.onNodeWithText("Language").assertIsDisplayed()
        rule.onNodeWithText("English").assertIsDisplayed()
        rule.onNodeWithText("한국어").performClick()
        assertEquals(AppLanguage.Option.KO, themeVm.languageOption.value)
    }

    @Test
    @Config(qualifiers = "en")
    fun `영어 모드에서 빈 채팅 화면 문구가 영어로 표시된다`() {
        rule.setContent {
            KosmosTheme(language = AppLanguage.EN) {
                EmptyChat(onStarter = {})
            }
        }
        rule.onNodeWithText("What can I help with today?").assertIsDisplayed()
    }
}
