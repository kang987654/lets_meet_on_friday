package com.kosmos.app.feature.settings

import com.kosmos.app.core.common.ResponseStyle
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.core.common.Constants
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.platform.speech.SpeechOutput

data class SettingsUiState(
    val responseStyle: String = ResponseStyle.DEFAULT,
    val maxTokens: Int = Constants.DEFAULT_CONTEXT_TOKENS,
    val modelLoadState: ModelLoadState = ModelLoadState.Loading,
    val briefingEnabled: Boolean = true,
    val briefingTimeMinutes: Int =
        SettingsDataStore.DEFAULT_BRIEFING_TIME_MINUTES,
    /** 대화 리셋 시점 자동 추출(C′2) — 기본 켜짐. */
    val autoExtractEnabled: Boolean = true,
    /** 답변 자동 낭독(0.29.0) — 기본 꺼짐. */
    val ttsAutoRead: Boolean = false,
    /** TTS 엔진 패키지, 빈 문자열 = 시스템 기본. */
    val ttsEngine: String = "",
    val voiceStatus: SpeechOutput.VoiceStatus =
        SpeechOutput.VoiceStatus.UNKNOWN
)
