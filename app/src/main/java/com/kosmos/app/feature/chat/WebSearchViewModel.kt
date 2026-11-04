package com.kosmos.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.library.LibraryRepository
import com.kosmos.app.domain.library.LibraryStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [WebSearchViewModel]
 * 채팅 입력바 검색 팝업의 토글 상태 — 웹 검색(2026-07-31)과 문서 검색(0.41.2).
 *
 * ### Architecture Context
 * - **Layer**: Feature / Presentation (Chat)
 * - **Dependencies**: [SettingsDataStore], [LibraryRepository](보관함에 쓸 문서가 있는지)
 *
 * ### Key Flow
 * 1. DataStore 의 `webSearchEnabledFlow`·`docSearchEnabledFlow`(둘 다 기본 false)를 StateFlow 로 노출합니다.
 * 2. 토글 변경 시 DataStore 에 영속하며, 에이전트(ContextBuilder)가 매 턴 값을 읽어 위키·문서 검색 툴의 노출을 정합니다.
 */
@HiltViewModel
class WebSearchViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    library: LibraryRepository
) : ViewModel() {

    val webSearchEnabled: StateFlow<Boolean> = settingsDataStore.webSearchEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val docSearchEnabled: StateFlow<Boolean> = settingsDataStore.docSearchEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 색인이 끝난 문서가 하나라도 있는가 — 없으면 팝업의 문서 검색이 비활성(0.41.2 Q2). */
    val docSearchAvailable: StateFlow<Boolean> = library.observeDocuments()
        .map { docs -> docs.any { it.status == LibraryStatus.READY } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setWebSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveWebSearchEnabled(enabled)
        }
    }

    fun setDocSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveDocSearchEnabled(enabled)
        }
    }
}
