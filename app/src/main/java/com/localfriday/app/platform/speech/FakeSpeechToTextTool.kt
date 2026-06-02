package com.localfriday.app.platform.speech

import com.localfriday.app.domain.tool.SpeechToTextTool
import com.localfriday.app.domain.tool.SttState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FakeSpeechToTextTool : SpeechToTextTool {

    private val _state = MutableStateFlow(SttState.IDLE)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow<String?>(null)
    override val transcript: StateFlow<String?> = _transcript.asStateFlow()

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    var simulatedResult: String = "이것은 가짜 음성 인식 결과입니다."
    var simulateError: Boolean = false

    override fun start(preferOffline: Boolean, language: String) {
        simulationJob?.cancel()
        _transcript.value = null
        _state.value = SttState.LISTENING

        simulationJob = scope.launch {
            delay(2000) // Simulate user speaking
            
            if (_state.value == SttState.LISTENING) {
                _state.value = SttState.TRANSCRIBING
            }

            delay(1000) // Simulate processing time

            if (simulateError) {
                _state.value = SttState.ERROR
            } else {
                _transcript.value = simulatedResult
                _state.value = SttState.DONE
            }
        }
    }

    override fun stop() {
        if (_state.value == SttState.LISTENING) {
            _state.value = SttState.TRANSCRIBING
        }
    }

    override fun cancel() {
        simulationJob?.cancel()
        _state.value = SttState.IDLE
        _transcript.value = null
    }
}
