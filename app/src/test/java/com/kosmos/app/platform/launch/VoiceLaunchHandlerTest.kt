package com.kosmos.app.platform.launch

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [VoiceLaunchHandlerTest]
 * replay=1 운반 계약을 고정합니다 — 콜드 스타트에서 늦은 구독자(ChatViewModel)가 스플래시
 * 통과 후에도 요청을 받아야 하고, 소비 후에는 재전달되면 안 된다 (ShareIntentHandler 전례).
 */
class VoiceLaunchHandlerTest {

    private val handler = VoiceLaunchHandler()

    private fun intent(action: String?): Intent = mockk {
        every { this@mockk.action } returns action
    }

    @Test
    fun `늦은 구독자도 음성 요청을 받는다 - replay 계약`() = runTest {
        handler.handleIntent(intent(VoiceLaunchHandler.ACTION_VOICE_INPUT))

        val received = withTimeoutOrNull(1_000) { handler.requests.first() }

        assertNotNull("구독 전에 도착한 요청이 유실됐다", received)
    }

    @Test
    fun `소비 후에는 재전달되지 않는다`() = runTest {
        handler.handleIntent(intent(VoiceLaunchHandler.ACTION_VOICE_INPUT))
        handler.requests.first()
        handler.clearConsumed()

        val replayed = withTimeoutOrNull(100) { handler.requests.first() }

        assertNull("소비한 요청이 재구독에서 다시 왔다", replayed)
    }

    @Test
    fun `무관한 액션과 null 인텐트는 무시한다`() = runTest {
        handler.handleIntent(null)
        handler.handleIntent(intent(Intent.ACTION_SEND))
        handler.handleIntent(intent(null))

        val received = withTimeoutOrNull(100) { handler.requests.first() }

        assertNull(received)
    }
}
