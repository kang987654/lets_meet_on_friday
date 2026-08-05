package com.kosmos.app.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.data.local.prefs.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [ThemeViewModel]
 * 앱 전역 테마 모드(시스템/라이트/다크)를 보관하고 DataStore에 영속하는 뷰모델입니다. (ADR-005)
 *
 * ### Architecture Context
 * - **Layer**: UI (Theme)
 * - **Dependencies**: [SettingsDataStore]
 *
 * ### Key Flow
 * 1. MainActivity가 이 뷰모델을 구독하여 [KosmosTheme]에 현재 모드를 전달합니다.
 * 2. 설정 화면의 테마 선택이 [setThemeMode]를 호출하면 DataStore에 저장되고,
 *    Flow가 갱신되어 앱 전체가 즉시 재구성됩니다.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsDataStore.themeModeFlow
        .map { ThemeMode.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsDataStore.saveThemeMode(mode.key)
        }
    }
}
