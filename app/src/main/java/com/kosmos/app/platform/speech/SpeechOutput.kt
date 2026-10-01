package com.kosmos.app.platform.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.data.local.prefs.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SpeechOutput]
 * 기기 내장 TTS 엔진(삼성·구글 등)으로 답변을 읽어 주는 래퍼입니다 (0.29.0, expand.md A2).
 *
 * ### Architecture Context
 * - **Layer**: Platform (Speech)
 * - **Dependencies**: Android [TextToSpeech], [SettingsDataStore](엔진 선택), [AudioManager](오디오 포커스)
 *
 * ### Key Flow
 * 1. [speak] — 설정의 엔진으로 [TextToSpeech] 를 (필요하면 다시) 만들고 초기화를 기다린다.
 * 2. 초기화가 끝나면 **한국어·오프라인** 음성만 고른다. 없으면 [VoiceStatus.NO_KOREAN_OFFLINE_VOICE] 로 낭독하지 않는다.
 * 3. [SpeakableText] 로 마크다운을 걷어내고 상한 이하로 나눠 큐에 넣는다. 마지막 조각이 끝나면 [speakingMessageId] 가 null.
 *
 * [WHY] 네트워크 음성으로 몰래 넘어가지 않는다 — 앱 전체가 오프라인 원칙(NFR1)이다. 음성 데이터가 없으면 설정 화면이
 * 엔진 설정으로 안내한다.
 * [WHY] `open` — 채팅 뷰모델 테스트가 엔진 없이 대역으로 갈아 끼운다(AGENTS §2-②, AudioRecorder 전례).
 * [WHY] init 에서 엔진을 만들지 않는다(§2-④) — 첫 [speak]/[availableEngines] 때 만든다. 낭독을 안 쓰는 사용자는 엔진 바인딩 비용이 없다.
 */
@Singleton
open class SpeechOutput @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore
) {
    /** 엔진·음성 준비 상태 — 설정 화면 안내에 쓴다. */
    enum class VoiceStatus { UNKNOWN, READY, NO_KOREAN_OFFLINE_VOICE, INIT_FAILED }

    /** 설치된 TTS 엔진. */
    data class Engine(val packageName: String, val label: String)

    private var tts: TextToSpeech? = null
    private var enginePackage: String? = null
    private var initialized = CompletableDeferred<Boolean>()

    @Volatile
    private var lastUtteranceId: String? = null
    private var focusRequest: AudioFocusRequest? = null

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    /** 지금 읽고 있는 메시지 id — 말풍선의 재생/정지 아이콘이 본다. */
    open val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private val _voiceStatus = MutableStateFlow(VoiceStatus.UNKNOWN)
    open val voiceStatus: StateFlow<VoiceStatus> = _voiceStatus.asStateFlow()

    /**
     * [markdown] 을 읽습니다. 다른 메시지를 읽던 중이면 끊고 새로 읽는다.
     * @return 실제로 낭독을 시작했으면 true (빈 텍스트·엔진 실패·한국어 오프라인 음성 없음이면 false)
     */
    open suspend fun speak(messageId: String, markdown: String): Boolean {
        val text = SpeakableText.from(markdown)
        if (text.isBlank()) return false
        val engine = ensureEngine(settings.ttsEngineFlow.first())
        val ready = withTimeoutOrNull(INIT_TIMEOUT_MS) { initialized.await() } ?: false
        if (!ready || _voiceStatus.value != VoiceStatus.READY) return false

        engine.stop()
        val chunks = SpeakableText.chunks(text, TextToSpeech.getMaxSpeechInputLength())
        requestFocus()
        _speakingMessageId.value = messageId
        lastUtteranceId = "$messageId#${chunks.lastIndex}"
        chunks.forEachIndexed { index, chunk ->
            engine.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$messageId#$index")
        }
        return true
    }

    /** 설정의 엔진을 준비하고 음성 상태를 확인합니다 — 설정 화면이 안내를 띄우려고 부른다. */
    open suspend fun prepare(): VoiceStatus {
        ensureEngine(settings.ttsEngineFlow.first())
        withTimeoutOrNull(INIT_TIMEOUT_MS) { initialized.await() }
        return _voiceStatus.value
    }

    /** 낭독을 멈춥니다. 읽고 있지 않으면 아무 일도 없다. */
    open fun stop() {
        if (_speakingMessageId.value == null) return
        tts?.stop()
        finish()
    }

    /** 설치된 엔진 목록 — 엔진이 아직 없으면 시스템 기본으로 하나 만들어 묻는다. */
    open fun availableEngines(): List<Engine> {
        val engine = tts ?: create(null)
        return runCatching { engine.engines.map { Engine(it.name, it.label) } }.getOrDefault(emptyList())
    }

    /** 엔진을 해제합니다 — 앱이 백그라운드로 갈 때 KosmosApp 이 부른다. 다음 [speak] 가 다시 만든다. */
    open fun release() {
        stop()
        tts?.shutdown()
        tts = null
        enginePackage = null
    }

    private fun ensureEngine(packageName: String): TextToSpeech {
        val wanted = packageName.ifBlank { null }
        val current = tts
        if (current != null && wanted == enginePackage) return current
        current?.shutdown()
        return create(wanted)
    }

    private fun create(packageName: String?): TextToSpeech {
        initialized = CompletableDeferred()
        _voiceStatus.value = VoiceStatus.UNKNOWN
        val listener = TextToSpeech.OnInitListener { status -> onInit(status) }
        val engine = if (packageName == null) {
            TextToSpeech(context, listener)
        } else {
            TextToSpeech(context, listener, packageName)
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId == lastUtteranceId) finish()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish()
            override fun onError(utteranceId: String?, errorCode: Int) = finish()
        })
        tts = engine
        enginePackage = packageName
        return engine
    }

    private fun onInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            AppLogger.w(TAG, "TTS 엔진 초기화 실패: status=$status engine=$enginePackage")
            _voiceStatus.value = VoiceStatus.INIT_FAILED
            initialized.complete(false)
            return
        }
        // [WHY] 한국어이면서 네트워크가 필요 없고 데이터가 설치된 음성만 — 품질이 높은 것부터.
        val voice = runCatching { engine.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == KOREAN && !it.isNetworkConnectionRequired }
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .maxByOrNull { it.quality }
        _voiceStatus.value = if (voice != null && engine.setVoice(voice) == TextToSpeech.SUCCESS) {
            VoiceStatus.READY
        } else {
            VoiceStatus.NO_KOREAN_OFFLINE_VOICE
        }
        initialized.complete(true)
    }

    private fun finish() {
        _speakingMessageId.value = null
        lastUtteranceId = null
        abandonFocus()
    }

    // [WHY] 짧게 끼어드는 낭독이라 TRANSIENT_MAY_DUCK — 음악은 줄였다가 끝나면 원래대로 돌아온다.
    private fun requestFocus() {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        audioManager.requestAudioFocus(request)
        focusRequest = request
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        context.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request)
        focusRequest = null
    }

    private companion object {
        const val TAG = "SpeechOutput"
        const val KOREAN = "ko"
        // [WHY] 엔진 바인딩은 보통 1초 안이지만 첫 실행(엔진 프로세스 기동)은 더 걸린다 — 넘으면 이번 낭독만 포기.
        const val INIT_TIMEOUT_MS = 5_000L
    }
}
