package com.kosmos.app.platform.speech

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AudioLevelTest]
 * 녹음 버퍼 → 소리 크기(0..1) — 입력바 파형이 읽는다(0.38.4 M5). 조용하면 0, 가장 큰 소리는 1, 그 사이는 dB 비율.
 */
@RunWith(RobolectricTestRunner::class)
class AudioLevelTest {

    private val recorder = AudioRecorder(ApplicationProvider.getApplicationContext())

    private fun pcm(vararg samples: Int): ByteArray =
        ByteArray(samples.size * 2).also { b -> samples.forEachIndexed { i, s -> b[i * 2] = (s and 0xFF).toByte(); b[i * 2 + 1] = (s shr 8).toByte() } }

    @Test
    fun `조용하면 0, 가장 큰 소리는 1, 중간 크기는 사이`() {
        assertEquals(0f, recorder.levelOf(pcm(0, 0, 0, 0), 8))
        assertEquals(1f, recorder.levelOf(pcm(32767, -32767, 32767, -32767), 8), 0.001f)
        val mid = recorder.levelOf(pcm(1000, -1000, 1000, -1000), 8) // 약 -30dBFS
        assertTrue("$mid", mid in 0.3f..0.5f)
        assertEquals("빈 버퍼", 0f, recorder.levelOf(ByteArray(0), 0))
    }

    @Test
    fun `녹음하지 않을 때 소리 크기는 0`() {
        assertEquals(0f, recorder.level.value)
    }
}
