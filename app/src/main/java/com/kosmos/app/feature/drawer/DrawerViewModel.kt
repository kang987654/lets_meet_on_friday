package com.kosmos.app.feature.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.ui.paging.DefaultPagingSource
import com.kosmos.app.ui.paging.unwrapForPaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * [DrawerViewModel]
 * 드로어의 기억 아카이브(에피소드 문서)와 검색을 담당합니다 (시안 A′ M2-4).
 *
 * ### Architecture Context
 * - **Layer**: Feature (Drawer)
 * - **Dependencies**: [EpisodeRepository]
 *
 * [WHY] 검색바와 SearchMemory 툴이 **같은 저장소·같은 검색 방식**(LIKE+태그, SUMMARIZED만)을
 * 쓴다 — 드로어에서 보이는 것과 모델이 회수하는 것이 어긋나면 "비서는 못 찾는데 나는 보이는"
 * 불신이 생긴다 (ui_a_prime.md 동작 규칙).
 */
@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val episodeRepository: EpisodeRepository
) : ViewModel() {

    /** 아카이브 목록 — MemoryViewModel 의 Pager 전례 그대로. */
    val episodePaging: Flow<PagingData<Episode>> =
        Pager(PagingConfig(pageSize = 20)) {
            DefaultPagingSource { offset, limit ->
                episodeRepository.getEpisodes(offset, limit).unwrapForPaging()
            }
        }.flow.cachedIn(viewModelScope)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /**
     * 검색 결과. 질의가 비면 null — 화면은 아카이브 페이징 목록으로 되돌아간다.
     *
     * [WHY] 300ms 디바운스 — 타이핑마다 LIKE 두 방(본문+태그)을 쏘지 않기 위해서다.
     */
    @OptIn(FlowPreview::class)
    val searchResults: StateFlow<List<Episode>?> = _query
        .debounce(300)
        .map { q ->
            if (q.isBlank()) return@map null
            val byContent = (episodeRepository.search(q) as? AppResult.Success)?.data.orEmpty()
            val byTag = (episodeRepository.searchByTags(q) as? AppResult.Success)?.data.orEmpty()
            (byContent + byTag).distinctBy { it.id }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChanged(value: String) {
        _query.value = value
    }
}
