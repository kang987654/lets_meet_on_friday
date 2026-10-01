package com.kosmos.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.runtime.gemma.GemmaRuntimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val runtimeManager: GemmaRuntimeManager,
    private val modelRunner: com.kosmos.app.domain.modelrunner.ModelRunner,
    private val briefingScheduler: com.kosmos.app.work.BriefingNotificationScheduler,
    private val speechOutput: com.kosmos.app.platform.speech.SpeechOutput
) : ViewModel() {

    private val _ttsEngines = kotlinx.coroutines.flow.MutableStateFlow<List<com.kosmos.app.platform.speech.SpeechOutput.Engine>>(emptyList())
    /** 설치된 TTS 엔진 — 화면 진입 시 [loadTtsEngines] 가 채운다. */
    val ttsEngines: StateFlow<List<com.kosmos.app.platform.speech.SpeechOutput.Engine>> = _ttsEngines

    // [WHY] combine 은 vararg 없는 오버로드가 5개까지다 — 0.20.0 에서 상한에 닿았고, 여섯째
    // 설정(자동 추출, C′2)부터는 **모델·응답 묶음 / 비서 동작 묶음** 두 그룹의 중첩 combine 으로
    // 간다. 그룹 경계가 설정 화면의 섹션 경계와 같아 다음 설정도 자기 그룹에 붙이면 된다.
    // 음성(0.29.0)은 셋째 그룹이다.
    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            settingsDataStore.responseStyleFlow,
            settingsDataStore.maxTokensFlow,
            runtimeManager.loadState
        ) { responseStyle, maxTokens, loadState -> Triple(responseStyle, maxTokens, loadState) },
        combine(
            settingsDataStore.briefingEnabledFlow,
            settingsDataStore.briefingTimeMinutesFlow,
            settingsDataStore.autoExtractEnabledFlow
        ) { briefingEnabled, briefingTimeMinutes, autoExtractEnabled ->
            Triple(briefingEnabled, briefingTimeMinutes, autoExtractEnabled)
        },
        combine(
            settingsDataStore.ttsAutoReadFlow,
            settingsDataStore.ttsEngineFlow,
            speechOutput.voiceStatus
        ) { autoRead, engine, voiceStatus -> Triple(autoRead, engine, voiceStatus) }
    ) { (responseStyle, maxTokens, loadState), (briefingEnabled, briefingTimeMinutes, autoExtractEnabled),
        (ttsAutoRead, ttsEngine, voiceStatus) ->
        SettingsUiState(
            responseStyle = responseStyle,
            maxTokens = maxTokens,
            modelLoadState = loadState,
            briefingEnabled = briefingEnabled,
            briefingTimeMinutes = briefingTimeMinutes,
            autoExtractEnabled = autoExtractEnabled,
            ttsAutoRead = ttsAutoRead,
            ttsEngine = ttsEngine,
            voiceStatus = voiceStatus
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun onResponseStyleChanged(style: String) {
        viewModelScope.launch {
            settingsDataStore.saveResponseStyle(style)
        }
    }

    fun onMaxTokensChanged(tokens: Int) {
        viewModelScope.launch {
            settingsDataStore.saveMaxTokens(tokens)
        }
    }

    fun onBriefingEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveBriefingEnabled(enabled)
            // [WHY] 저장 후 예약을 즉시 동기화한다 — off 인데 예약이 남으면 빈 워커가 매일 돌고,
            // on 인데 예약이 없으면 알림이 영영 안 온다 (off 워커는 자기 재예약을 안 남기므로).
            if (enabled) {
                briefingScheduler.reschedule(settingsDataStore.briefingTimeMinutesFlow.first())
            } else {
                briefingScheduler.cancel()
            }
        }
    }

    /** 자동 추출(C′2) 토글 — OFF 면 추출 oneShot 자체가 돌지 않는다(EpisodeFactExtractor 게이트). */
    fun onAutoExtractEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveAutoExtractEnabled(enabled)
        }
    }

    /** 답변 자동 낭독 토글. 끄면 읽던 것도 멈춘다. */
    fun onTtsAutoReadChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveTtsAutoRead(enabled)
            if (!enabled) speechOutput.stop()
        }
    }

    /** TTS 엔진 선택 — 저장 후 그 엔진을 준비해 한국어 오프라인 음성 여부를 바로 보여 준다. */
    fun onTtsEngineChanged(enginePackage: String) {
        viewModelScope.launch {
            settingsDataStore.saveTtsEngine(enginePackage)
            speechOutput.prepare()
        }
    }

    /** 설정 화면 진입 시 — 엔진 목록과 현재 엔진의 음성 상태를 읽는다. */
    fun loadTtsEngines() {
        viewModelScope.launch {
            speechOutput.prepare()
            _ttsEngines.value = speechOutput.availableEngines()
        }
    }

    fun onBriefingTimeChanged(minutes: Int) {
        viewModelScope.launch {
            settingsDataStore.saveBriefingTimeMinutes(minutes)
            if (settingsDataStore.briefingEnabledFlow.first()) {
                briefingScheduler.reschedule(minutes)
            }
        }
    }

    fun refreshModelState() {
        // [WHY] checkModelFile 만 부르면 상태가 FileFound 에서 멈춘다 — FileFound → Ready 로
        // 옮기는 유일한 경로는 warmUp 인데, 그 반응은 스플래시 뷰모델에만 있어 이 화면에는
        // 없었다(2026-08-14 실기기 스모크: 백그라운드 해제 후 재진입 시 스피너 정지).
        // warmUp 은 파일 재탐색을 포함하고, 엔진이 살아 있으면 초기화를 건너뛴다.
        viewModelScope.launch { modelRunner.warmUp() }
    }
}
