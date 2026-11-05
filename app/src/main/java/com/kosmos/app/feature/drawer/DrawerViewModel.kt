package com.kosmos.app.feature.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.assistant.tool.MemorySemanticRanker
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.memory.MemoryVectorKey
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.kosmos.app.domain.memory.ProfileSuggestionRepository

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
    suggestionRepository: ProfileSuggestionRepository,
    // 뜻 검색(0.41.3) — 없거나 준비 전이면 어휘 검색만(단위 테스트 생성부 호환).
    private val semantic: MemorySemanticRanker? = null
) : ViewModel() {

    /** 대기 중 프로필 제안 수(C′2) — 프로필 카드 부제에 "제안 N건"으로 표시. 종결은 채팅 카드에서. */
    val pendingSuggestionCount: StateFlow<Int> =
        suggestionRepository.observePending().map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
    // [WHY] 시트에서 수정·삭제하면 같은 질의라도 결과가 달라진다 — 질의가 안 바뀌어도 다시 돌릴 신호.
    private val searchRefresh = MutableStateFlow(0)

    @OptIn(FlowPreview::class)
    val searchResults: StateFlow<List<Episode>?> = _query
        .debounce(300)
        .combine(searchRefresh) { q, _ -> q }
        .map { q -> search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    internal suspend fun search(q: String): List<Episode>? {
        if (q.isBlank()) return null
        val terms = q.trim().split(WHITESPACE).filter { it.isNotEmpty() }.distinct().take(MAX_TERMS)
        // [WHY] 질의 전체에 더해 낱말마다도 찾는다(0.41.3) — 전체 LIKE 는 "치과 예약 변경" 처럼 붙은 글자가 그대로 있어야만 맞아 두 낱말 이상이면
        // 거의 비었다. SearchMemory 툴은 처음부터 낱말별이었다 — 드로어와 툴이 같은 기억을 보게 한다.
        val queries = (listOf(q.trim()) + terms).distinct()
        val precise = queries.flatMap { term ->
            (episodeRepository.search(term) as? AppResult.Success)?.data.orEmpty() +
                (episodeRepository.searchByTags(term) as? AppResult.Success)?.data.orEmpty()
        }.distinctBy { it.id }
        var scanned: List<Episode>? = null
        suspend fun all(): List<Episode> = scanned
            ?: (episodeRepository.getEpisodes(0, Constants.MEMORY_SCAN_LIMIT) as? AppResult.Success)?.data.orEmpty().also { scanned = it }
        val pool = precise.ifEmpty {
            all().filter { BigramMatcher.bestTermOverlap(terms, it.searchText()) >= Constants.BIGRAM_MIN_TERM_OVERLAP }
        }
        val lexical = pool.sortedWith(
            compareByDescending<Episode> { BigramMatcher.score(terms, it.searchText()) }
                .thenByDescending { it.createdAt }
        )
        // [WHY] 뜻 검색이 준비됐으면 결합 순위 상위 [SEMANTIC_TOP] 를 맨 앞에, 그 뒤에 글자로 찾은 나머지 — 화면 목록이라 전부를 뜻 순서로 늘어놓으면
        // 무관한 대화가 줄줄이 보인다. 준비 전·모델 없음이면 글자 검색 그대로.
        val ranker = semantic ?: return lexical
        val candidates = all().filter { it.title != null }
        val sparse = candidates.associate { MemoryVectorKey.episode(it.id) to BigramMatcher.score(terms, it.searchText()) }
        val top = ranker.rank(q.trim(), sparse, SEMANTIC_TOP) ?: return lexical
        val byId = candidates.associateBy { it.id }
        val head = top.mapNotNull { byId[it.refId] }
        return head + lexical.filter { e -> head.none { it.id == e.id } }
    }

    fun onQueryChanged(value: String) {
        _query.value = value
    }

    /** 에피소드 시트에서 수정·삭제가 성공했다 — 검색 결과를 다시 계산한다(아카이브 목록은 화면이 refresh). */
    fun onEpisodesChanged() {
        searchRefresh.value += 1
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        /** SearchMemoryToolExecutor.MAX_TOKENS 와 같은 상한 — 문장을 통째로 넣어도 항이 폭주하지 않게. */
        const val MAX_TERMS = 4

        /** 뜻 검색 결과를 앞에 몇 개 둘지 — 툴(3)보다 넉넉히, 화면이 무관한 대화로 차지 않게 작게. */
        const val SEMANTIC_TOP = 5
    }
}
