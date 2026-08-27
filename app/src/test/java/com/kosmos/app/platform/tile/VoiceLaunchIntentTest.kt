package com.kosmos.app.platform.tile

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.MainActivity
import com.kosmos.app.platform.launch.VoiceLaunchHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [VoiceLaunchIntentTest]
 * 위젯 🎤·QS 타일이 공유하는 진입 인텐트의 계약을 고정합니다 — 액션이 어긋나면
 * VoiceLaunchHandler 가 무시해 "탭했는데 아무 일도 없음"이 된다.
 */
@RunWith(RobolectricTestRunner::class)
class VoiceLaunchIntentTest {

    @Test
    fun `음성 진입 인텐트는 핸들러 액션과 MainActivity 를 가리킨다`() {
        val intent = voiceLaunchIntent(ApplicationProvider.getApplicationContext())

        assertEquals(VoiceLaunchHandler.ACTION_VOICE_INPUT, intent.action)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun `singleTop 짝 플래그가 전부 실린다`() {
        // [WHY] SINGLE_TOP 이 빠지면 매니페스트의 launchMode 와 무관하게 재생성 경로를 탈 수
        // 있고, NEW_TASK 가 빠지면 액티비티 밖(타일)에서 시작이 거부된다.
        val flags = voiceLaunchIntent(ApplicationProvider.getApplicationContext()).flags

        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }
}
