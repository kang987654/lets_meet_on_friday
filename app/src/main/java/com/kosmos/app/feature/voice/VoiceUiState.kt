package com.kosmos.app.feature.voice

sealed class VoiceUiState {
    object Idle : VoiceUiState()
    object Listening : VoiceUiState()
    object Transcribing : VoiceUiState()
    data class Success(val transcript: String) : VoiceUiState()
    data class Error(val message: String) : VoiceUiState()
}
