package com.kosmos.app.ui.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.domain.tool.SpeechToTextTool
import com.kosmos.app.domain.tool.SttState
import com.kosmos.app.domain.usecase.ProcessVoiceInputUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val speechToTextTool: SpeechToTextTool,
    private val processVoiceInputUseCase: ProcessVoiceInputUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            speechToTextTool.state.collect { sttState ->
                when (sttState) {
                    SttState.IDLE -> _uiState.value = VoiceUiState.Idle
                    SttState.LISTENING -> _uiState.value = VoiceUiState.Listening
                    SttState.TRANSCRIBING -> _uiState.value = VoiceUiState.Transcribing
                    SttState.DONE -> {
                        val transcript = speechToTextTool.transcript.value
                        if (!transcript.isNullOrBlank()) {
                            _uiState.value = VoiceUiState.Success(transcript)
                        } else {
                            _uiState.value = VoiceUiState.Error("음성을 인식하지 못했습니다.")
                        }
                    }
                    SttState.ERROR -> _uiState.value = VoiceUiState.Error("음성 인식 중 오류가 발생했습니다.")
                }
            }
        }
    }

    fun startListening() {
        speechToTextTool.start()
    }

    fun stopListening() {
        speechToTextTool.stop()
    }

    fun sendTranscript(sessionId: String, transcript: String) {
        viewModelScope.launch {
            processVoiceInputUseCase(sessionId, transcript)
            reset()
        }
    }

    fun reset() {
        speechToTextTool.cancel()
        _uiState.value = VoiceUiState.Idle
    }
}
