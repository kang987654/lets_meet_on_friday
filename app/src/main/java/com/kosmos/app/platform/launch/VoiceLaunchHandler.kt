package com.kosmos.app.platform.launch

import android.content.Intent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [VoiceLaunchHandler]
 * 위젯 🎤·QS 타일의 "음성 입력으로 열기" 요청을 채팅 화면까지 운반합니다 (A3).
 *
 * ### Architecture Context
 * - **Layer**: App (Platform)
 * - **Dependencies**: 없음
 *
 * [WHY] replay=1 — 콜드 스타트는 스플래시(모델 로드 9~12초)를 지나야 구독자(ChatViewModel)가
 * 생기므로, 그 전에 도착한 요청이 유실되면 안 된다. 소비 후 [clearConsumed] 로 재전달을 막는다
 * (ShareIntentHandler 전례).
 */
@Singleton
class VoiceLaunchHandler @Inject constructor() {

    private val _requests = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val requests = _requests.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearConsumed() {
        _requests.resetReplayCache()
    }

    fun handleIntent(intent: Intent?) {
        if (intent?.action != ACTION_VOICE_INPUT) return
        _requests.tryEmit(Unit)
    }

    companion object {
        const val ACTION_VOICE_INPUT = "com.kosmos.app.action.VOICE_INPUT"
    }
}
