package com.kosmos.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.assistant.context.projectedProfileTokens
import com.kosmos.app.assistant.context.renderProfileBlock
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.mapper.ErrorMessages
import com.kosmos.app.domain.memory.ProfileRepository
import com.kosmos.app.domain.model.ProfileEntry
import com.kosmos.app.domain.tool.Tokenizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [ProfileSheetViewModel]
 * 프로필 키-값 항목의 편집(추가·수정·삭제)과 토큰 상한 집행을 담당합니다 (C′1).
 *
 * ### Architecture Context
 * - **Layer**: Feature (Profile)
 * - **Dependencies**: [ProfileRepository], [Tokenizer]
 *
 * ### Key Flow
 * 1. [entries] 를 구독해 시트에 표시, [tokenUsage] 가 게이지("N/100")를 댄다
 * 2. [upsert] 는 저장 **전에** 편집 반영본 전체를 렌더해 상한을 검사 — 초과면 저장 차단
 *    ([Tokenizer] 추정은 과대 방향이라 상한 집행에 안전, exp26)
 */
@HiltViewModel
class ProfileSheetViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val tokenizer: Tokenizer
) : ViewModel() {

    val entries: StateFlow<List<ProfileEntry>> = profileRepository.observeEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tokenUsage: StateFlow<Int> = entries
        .map { tokenizer.sizeInTokens(renderProfileBlock(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // [WHY] onSaved 콜백 — 저장이 끝난 뒤에만 입력란을 비운다(MemoryViewModel.addTask 전례).
    // 상한 초과·저장 실패 시 입력이 남아 있어야 사용자가 고쳐서 재시도할 수 있다.
    fun upsert(key: String, value: String, onSaved: () -> Unit = {}) {
        val trimmedKey = key.trim()
        val trimmedValue = value.trim()
        if (trimmedKey.isEmpty() || trimmedValue.isEmpty()) return
        viewModelScope.launch {
            // [WHY] 저장 전에 "편집이 반영된 전체"를 렌더해 검사한다 — 항목 단위 검사는
            // 합계 초과를 못 막는다. 초과분은 저장 자체를 차단해 예산 불변식을 지킨다.
            val estimated = projectedProfileTokens(entries.value, trimmedKey, trimmedValue, tokenizer)
            if (estimated > Constants.PROFILE_MAX_TOKENS) {
                _error.value = "프로필이 너무 길어요 (${estimated}/${Constants.PROFILE_MAX_TOKENS}토큰) — 항목을 줄이거나 짧게 써 주세요."
                return@launch
            }
            when (val result = profileRepository.upsert(trimmedKey, trimmedValue)) {
                is AppResult.Success -> onSaved()
                is AppResult.Failure -> _error.value = ErrorMessages.userMessage(result.error)
            }
        }
    }

    fun delete(key: String) {
        viewModelScope.launch {
            val result = profileRepository.delete(key)
            if (result is AppResult.Failure) {
                _error.value = ErrorMessages.userMessage(result.error)
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
