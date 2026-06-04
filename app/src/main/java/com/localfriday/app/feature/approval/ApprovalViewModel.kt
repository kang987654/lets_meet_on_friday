package com.localfriday.app.feature.approval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.model.CalendarDraft
import com.localfriday.app.domain.usecase.ApproveAndSaveEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApprovalViewModel @Inject constructor(
    private val approveAndSaveEventUseCase: ApproveAndSaveEventUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ApprovalUiState>(ApprovalUiState.Idle)
    val uiState: StateFlow<ApprovalUiState> = _uiState.asStateFlow()

    fun approve(sessionId: String, draft: CalendarDraft) {
        viewModelScope.launch {
            _uiState.value = ApprovalUiState.Saving
            when (val result = approveAndSaveEventUseCase(sessionId, draft, isApproved = true)) {
                is AppResult.Success -> {
                    _uiState.value = ApprovalUiState.Success
                }
                is AppResult.Failure -> {
                    _uiState.value = ApprovalUiState.Error(result.error.toString())
                }
            }
        }
    }

    fun reject(sessionId: String, draft: CalendarDraft) {
        viewModelScope.launch {
            _uiState.value = ApprovalUiState.Saving
            // 거절 시 오류가 나더라도 사용자는 화면을 닫는 것이 목적이므로 보통 Success 처리됩니다.
            when (val result = approveAndSaveEventUseCase(sessionId, draft, isApproved = false)) {
                is AppResult.Success -> {
                    _uiState.value = ApprovalUiState.Success
                }
                is AppResult.Failure -> {
                    _uiState.value = ApprovalUiState.Error(result.error.toString())
                }
            }
        }
    }
}
