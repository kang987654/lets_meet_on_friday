package com.localfriday.app.platform.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.localfriday.app.domain.tool.SpeechToTextTool
import com.localfriday.app.domain.tool.SttState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSpeechToTextTool @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechToTextTool, RecognitionListener {

    private val _state = MutableStateFlow(SttState.IDLE)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow<String?>(null)
    override val transcript: StateFlow<String?> = _transcript.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    override fun start(preferOffline: Boolean, language: String) {
        // Run on main thread usually required for SpeechRecognizer, 
        // but assuming this is called appropriately or will throw if not.
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        _transcript.value = null
        _state.value = SttState.LISTENING
        speechRecognizer?.startListening(intent)
    }

    override fun stop() {
        if (_state.value == SttState.LISTENING || _state.value == SttState.TRANSCRIBING) {
            speechRecognizer?.stopListening()
            _state.value = SttState.TRANSCRIBING
        }
    }

    override fun cancel() {
        speechRecognizer?.cancel()
        _state.value = SttState.IDLE
        _transcript.value = null
    }

    // --- RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        _state.value = SttState.LISTENING
    }

    override fun onBeginningOfSpeech() {
        // User started speaking
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Sound level changes, ignore for V0
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Ignore
    }

    override fun onEndOfSpeech() {
        // User stopped speaking, now transcribing
        if (_state.value == SttState.LISTENING) {
            _state.value = SttState.TRANSCRIBING
        }
    }

    override fun onError(error: Int) {
        _state.value = SttState.ERROR
        // Optionally map specific SpeechRecognizer errors to a generic error message,
        // but for V0, moving to ERROR state is sufficient.
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            _transcript.value = matches[0] // take best match
            _state.value = SttState.DONE
        } else {
            // No matches found
            _transcript.value = ""
            _state.value = SttState.DONE
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        // V0: Ignore partial results
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        // Ignore
    }
}
