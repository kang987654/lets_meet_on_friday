package com.kosmos.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.runtime.gemma.GemmaRuntimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val runtimeManager: GemmaRuntimeManager,
    private val modelRunner: com.kosmos.app.domain.modelrunner.ModelRunner
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsDataStore.responseStyleFlow,
        settingsDataStore.maxTokensFlow,
        runtimeManager.loadState
    ) { responseStyle, maxTokens, loadState ->
        SettingsUiState(
            responseStyle = responseStyle,
            maxTokens = maxTokens,
            modelLoadState = loadState
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

    fun refreshModelState() {
        // [WHY] checkModelFile 만 부르면 상태가 FileFound 에서 멈춘다 — FileFound → Ready 로
        // 옮기는 유일한 경로는 warmUp 인데, 그 반응은 스플래시 뷰모델에만 있어 이 화면에는
        // 없었다(2026-08-14 실기기 스모크: 백그라운드 해제 후 재진입 시 스피너 정지).
        // warmUp 은 파일 재탐색을 포함하고, 엔진이 살아 있으면 초기화를 건너뛴다.
        viewModelScope.launch { modelRunner.warmUp() }
    }
}
