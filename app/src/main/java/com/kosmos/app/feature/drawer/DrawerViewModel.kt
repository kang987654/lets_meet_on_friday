package com.kosmos.app.feature.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.search.BigramMatcher
import com.kosmos.app.domain.search.searchText
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
    private val episodeRepository: EpisodeRepository,
    profileRepository: com.kosmos.app.domain.memory.ProfileRepository,
    suggestionRepository: com.kosmos.app.domain.memory.ProfileSuggestionRepository
) : ViewModel() {

    /** 대기 중 프로필 제안 수(C′2) — 프로필 카드 부제에 "제안 N건"으로 표시. 종결은 채팅 카드에서. */
    val pendingSuggestionCount: StateFlow<Int> =
        suggestionRepository.observePending().map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 프로필 고정 카드(A′-2) — 상시 주입 기억의 열람 창. 빈 목록이면 등록 안내 상태. */
    val profileEntries: StateFlow<List<com.kosmos.app.domain.model.ProfileEntry>> =
        profileRepository.observeEntries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
     *
     * [WHY] `SearchMemory` 툴과 **같은 3단**(정밀 LIKE·태그 → 바이그램 점수 정렬 → 0건이면 전수 스캔 +
     * 최소 겹침 임계)이다 — 드로어에서 보이는 것과 모델이 회수하는 것이 어긋나면 "비서는 못 찾는데
     * 나는 보이는" 불신이 생긴다(ui_a_prime.md 동작 규칙). 모델 확장(C′3)만 없다 — 타이핑 디바운스
     * 300ms 안에 추론을 걸 수 없다.
     */
    @OptIn(FlowPreview::class)
    val searchResults: StateFlow<List<Episode>?> = _query
        .debounce(300)
        .map { q -> search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    internal suspend fun search(q: String): List<Episode>? {
        if (q.isBlank()) return null
        val terms = q.trim().split(WHITESPACE).filter { it.isNotEmpty() }.take(MAX_TERMS)
        val byContent = (episodeRepository.search(q) as? AppResult.Success)?.data.orEmpty()
        val byTag = (episodeRepository.searchByTags(q) as? AppResult.Success)?.data.orEmpty()
        val precise = (byContent + byTag).distinctBy { it.id }
        val pool = if (precise.isNotEmpty()) {
            precise
        } else {
            (episodeRepository.getEpisodes(0, Constants.MEMORY_SCAN_LIMIT) as? AppResult.Success)?.data.orEmpty()
                .filter { BigramMatcher.bestTermOverlap(terms, it.searchText()) >= Constants.BIGRAM_MIN_TERM_OVERLAP }
        }
        return pool.sortedWith(
            compareByDescending<Episode> { BigramMatcher.score(terms, it.searchText()) }
                .thenByDescending { it.createdAt }
        )
    }

    fun onQueryChanged(value: String) {
        _query.value = value
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        /** SearchMemoryToolExecutor.MAX_TOKENS 와 같은 상한 — 문장을 통째로 넣어도 항이 폭주하지 않게. */
        const val MAX_TERMS = 4
    }
}
