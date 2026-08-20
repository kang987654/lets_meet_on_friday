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
    private val briefingScheduler: com.kosmos.app.work.BriefingNotificationScheduler
) : ViewModel() {

    // [WHY] combine 은 vararg 없는 오버로드가 5개까지다 — 여기서 딱 상한에 닿았다.
    // 다음 설정부터는 설정 플로우들을 data class 로 묶는 리팩터가 필요하다.
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsDataStore.responseStyleFlow,
        settingsDataStore.maxTokensFlow,
        runtimeManager.loadState,
        settingsDataStore.briefingEnabledFlow,
        settingsDataStore.briefingTimeMinutesFlow
    ) { responseStyle, maxTokens, loadState, briefingEnabled, briefingTimeMinutes ->
        SettingsUiState(
            responseStyle = responseStyle,
            maxTokens = maxTokens,
            modelLoadState = loadState,
            briefingEnabled = briefingEnabled,
            briefingTimeMinutes = briefingTimeMinutes
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
