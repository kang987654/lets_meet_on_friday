package com.kosmos.app.feature.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.assistant.cleanup.MemoryCleanupRunner
import com.kosmos.app.domain.cleanup.MergeProposal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [MemoryCleanupViewModel]
 * 기억 정리 화면 — [MemoryCleanupRunner] 상태를 그대로 보여 주고, 병합 제안의 체크 상태만 따로 든다 (0.30.0).
 *
 * ### Architecture Context
 * - **Layer**: Feature / Presentation (Cleanup)
 * - **Dependencies**: [MemoryCleanupRunner](@Singleton — 화면을 떠나도 정리는 계속된다)
 *
 * [WHY] 체크 기본값은 **해제**다 — exp42: 프롬프트만으로 오병합 0 을 보장하지 못했다("와이파이 비밀번호" + "회사 와이파이 비밀번호").
 * 사용자가 원문과 합친 문장을 보고 직접 고른 것만 합친다.
 */
@HiltViewModel
class MemoryCleanupViewModel @Inject constructor(
    private val runner: MemoryCleanupRunner
) : ViewModel() {

    val state: StateFlow<MemoryCleanupRunner.State> = runner.state

    private val _checked = MutableStateFlow<Set<String>>(emptySet())
    /** 체크한 제안의 [MergeProposal.key]. */
    val checked: StateFlow<Set<String>> = _checked.asStateFlow()

    fun start() {
        _checked.value = emptySet()
        runner.start()
    }

    fun cancel() = runner.cancel()

    fun toggle(proposal: MergeProposal) {
        _checked.value = _checked.value.let { if (proposal.key in it) it - proposal.key else it + proposal.key }
    }

    /** 체크한 제안만 적용한다. */
    fun applyChecked() {
        val review = state.value as? MemoryCleanupRunner.State.Review ?: return
        val selected = review.proposals.filter { it.key in _checked.value }
        viewModelScope.launch { runner.apply(selected) }
    }

    /** 결과를 닫는다 — 다음에 들어오면 처음 화면. */
    fun finish() {
        _checked.value = emptySet()
        runner.reset()
    }
}
