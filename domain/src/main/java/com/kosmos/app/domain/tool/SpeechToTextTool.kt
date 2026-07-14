package com.kosmos.app.domain.tool

import kotlinx.coroutines.flow.StateFlow

/**
 * [v0] 음성 인식(STT) 도구
 */
interface SpeechToTextTool {
    val state: StateFlow<SttState>
    val transcript: StateFlow<String?>
    
    fun start(preferOffline: Boolean = true, language: String = "ko-KR")
    fun stop()
    fun cancel()
}

enum class SttState {
    IDLE,
    LISTENING,
    TRANSCRIBING,
    DONE,
    ERROR
}
