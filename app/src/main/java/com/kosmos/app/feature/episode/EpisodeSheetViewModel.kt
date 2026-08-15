package com.kosmos.app.feature.episode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Tags
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.Episode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [EpisodeSheetViewModel]
 * 에피소드 시트의 로드·수정·삭제를 담당합니다 (시안 A′-3, P4 통제권).
 *
 * ### Architecture Context
 * - **Layer**: Feature (Episode)
 * - **Dependencies**: [EpisodeRepository]
 */
@HiltViewModel
class EpisodeSheetViewModel @Inject constructor(
    private val episodeRepository: EpisodeRepository
) : ViewModel() {

    private val _episode = MutableStateFlow<Episode?>(null)
    val episode: StateFlow<Episode?> = _episode.asStateFlow()

    fun load(episodeId: String) {
        viewModelScope.launch {
            _episode.value = (episodeRepository.getById(episodeId) as? AppResult.Success)?.data
        }
    }

    fun save(title: String, tagsCsv: String, summary: String) {
        val current = _episode.value ?: return
        viewModelScope.launch {
            val updated = current.copy(
                title = title.trim().ifEmpty { current.title },
                tags = Tags.normalizeAll(tagsCsv.split(",")),
                summary = summary.trim().ifEmpty { current.summary },
                updatedAt = System.currentTimeMillis()
            )
            if (episodeRepository.update(updated) is AppResult.Success) {
                _episode.value = updated
            }
        }
    }

    fun delete() {
        val current = _episode.value ?: return
        viewModelScope.launch {
            episodeRepository.delete(current.id)
            _episode.value = null
        }
    }
}
