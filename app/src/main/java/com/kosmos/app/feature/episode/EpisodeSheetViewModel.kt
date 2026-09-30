package com.kosmos.app.feature.episode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Tags
import com.kosmos.app.core.mapper.ErrorMessages
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.Episode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 에피소드 시트의 로드 상태입니다.
 *
 * [WHY] 예전에는 `Episode?` 하나로 "불러오는 중"과 "없음/실패"를 구분하지 못해, 이미 삭제된 id
 * (회수 칩이 가리키던 에피소드를 드로어에서 지운 뒤)나 조회 실패에서 시트가 "불러오는 중…"에
 * 영원히 머물렀다.
 */
sealed interface EpisodeSheetState {
    data object Loading : EpisodeSheetState
    data class Loaded(val episode: Episode) : EpisodeSheetState
    data object Missing : EpisodeSheetState
    data class Error(val message: String) : EpisodeSheetState
}

/**
 * [EpisodeSheetViewModel]
 * 에피소드 시트의 로드·수정·삭제를 담당합니다 (시안 A′-3, P4 통제권).
 *
 * ### Architecture Context
 * - **Layer**: Feature (Episode)
 * - **Dependencies**: [EpisodeRepository]
 *
 * ### Key Flow
 * 1. [load] — 결과를 [EpisodeSheetState] 로 노출한다.
 * 2. [save]/[delete] — 성공 시에만 콜백을 부르고(목록 새로고침은 호출자 몫), 실패는 [actionError].
 */
@HiltViewModel
class EpisodeSheetViewModel @Inject constructor(
    private val episodeRepository: EpisodeRepository
) : ViewModel() {

    private val _state = MutableStateFlow<EpisodeSheetState>(EpisodeSheetState.Loading)
    val state: StateFlow<EpisodeSheetState> = _state.asStateFlow()

    /** 수정·삭제 실패 안내. 소비 후 [dismissActionError]. */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun load(episodeId: String) {
        _state.value = EpisodeSheetState.Loading
        viewModelScope.launch {
            _state.value = when (val result = episodeRepository.getById(episodeId)) {
                is AppResult.Success -> result.data?.let { EpisodeSheetState.Loaded(it) } ?: EpisodeSheetState.Missing
                is AppResult.Failure -> EpisodeSheetState.Error(ErrorMessages.userMessage(result.error))
            }
        }
    }

    fun save(title: String, tagsCsv: String, summary: String, now: Long = System.currentTimeMillis(), onSaved: () -> Unit = {}) {
        val current = (_state.value as? EpisodeSheetState.Loaded)?.episode ?: return
        viewModelScope.launch {
            val updated = current.copy(
                title = title.trim().ifEmpty { current.title },
                tags = Tags.normalizeAll(tagsCsv.split(",")),
                summary = summary.trim().ifEmpty { current.summary },
                updatedAt = now
            )
            when (val result = episodeRepository.update(updated)) {
                is AppResult.Success -> {
                    _state.value = EpisodeSheetState.Loaded(updated)
                    onSaved()
                }
                is AppResult.Failure -> _actionError.value = ErrorMessages.userMessage(result.error)
            }
        }
    }

    fun delete(onDeleted: () -> Unit = {}) {
        val current = (_state.value as? EpisodeSheetState.Loaded)?.episode ?: return
        viewModelScope.launch {
            when (val result = episodeRepository.delete(current.id)) {
                is AppResult.Success -> {
                    _state.value = EpisodeSheetState.Missing
                    onDeleted()
                }
                is AppResult.Failure -> _actionError.value = ErrorMessages.userMessage(result.error)
            }
        }
    }

    fun dismissActionError() {
        _actionError.value = null
    }
}
