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
    private val runtimeManager: GemmaRuntimeManager
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
        runtimeManager.checkModelFile()
    }
}
