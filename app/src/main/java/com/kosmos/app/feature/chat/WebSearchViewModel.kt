package com.kosmos.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.data.local.prefs.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [WebSearchViewModel]
 * 채팅 헤더의 웹 검색 허용 토글 상태를 관리하는 뷰모델입니다. (2026-07-31 기획 변경)
 *
 * ### Architecture Context
 * - **Layer**: Feature / Presentation (Chat)
 * - **Dependencies**: [SettingsDataStore]
 *
 * ### Key Flow
 * 1. DataStore의 `webSearchEnabledFlow`(기본 false, 프라이버시 우선)를 StateFlow로 노출합니다.
 * 2. 토글 변경 시 DataStore에 영속하며, 에이전트(ContextBuilder)가 매 턴 해당 값을 읽어
 *    SearchWikipedia 툴의 노출/실행 허용을 결정합니다.
 */
@HiltViewModel
class WebSearchViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val webSearchEnabled: StateFlow<Boolean> = settingsDataStore.webSearchEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setWebSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveWebSearchEnabled(enabled)
        }
    }
}
