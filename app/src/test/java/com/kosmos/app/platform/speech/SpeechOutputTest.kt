package com.kosmos.app.platform.speech

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.data.local.prefs.SettingsDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

/**
 * [SpeechOutputTest]
 * 한국어 오프라인 음성이 있을 때만 읽고, 네트워크 음성으로 넘어가지 않으며, 정지 시 상태가 비는지 고정합니다 (0.29.0).
 */
@RunWith(RobolectricTestRunner::class)
class SpeechOutputTest {

    private val settings: SettingsDataStore = mockk { every { ttsEngineFlow } returns flowOf("") }
    private val output = SpeechOutput(ApplicationProvider.getApplicationContext(), settings)

    @After
    fun tearDown() = ShadowTextToSpeech.reset()

    private fun koreanVoice(networkRequired: Boolean) =
        Voice("ko-test", Locale.KOREAN, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, networkRequired, emptySet())

    /** speak 가 엔진을 만든 뒤 초기화 완료를 흉내 낸다 — 섀도는 OnInitListener 를 스스로 부르지 않는다. */
    private suspend fun speakWithInit(text: String): Boolean = kotlinx.coroutines.coroutineScope {
        val result = async { output.speak("m1", text) }
        yield()
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowOf(tts).onInitListener.onInit(TextToSpeech.SUCCESS)
        result.await()
    }

    @Test
    fun `한국어 오프라인 음성이 있으면 마크다운을 걷어내고 읽는다`() = runTest {
        ShadowTextToSpeech.addVoice(koreanVoice(networkRequired = false))

        assertTrue(speakWithInit("**치과** 예약은 3시예요."))

        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        assertEquals(listOf("치과 예약은 3시예요."), shadowOf(tts).spokenTextList)
        assertEquals("m1", output.speakingMessageId.value)
        assertEquals(SpeechOutput.VoiceStatus.READY, output.voiceStatus.value)
    }

    @Test
    fun `네트워크 음성만 있으면 읽지 않는다`() = runTest {
        ShadowTextToSpeech.addVoice(koreanVoice(networkRequired = true))

        assertFalse(speakWithInit("안녕하세요."))

        assertEquals(SpeechOutput.VoiceStatus.NO_KOREAN_OFFLINE_VOICE, output.voiceStatus.value)
        assertTrue(shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).spokenTextList.isEmpty())
    }

    @Test
    fun `정지하면 읽던 메시지가 비고 엔진이 멈춘다`() = runTest {
        ShadowTextToSpeech.addVoice(koreanVoice(networkRequired = false))
        speakWithInit("길게 읽을 답변입니다.")

        output.stop()

        assertNull(output.speakingMessageId.value)
        assertTrue(shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).isStopped)
    }
}
