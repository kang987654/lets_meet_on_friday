package com.kosmos.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.usecase.DownloadModelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * [ModelManagementViewModel]
 * 핵심 역할: 모델 관리(다운로드) 화면의 UI 상태를 관리하고 사용자 액션(다운로드 요청)을 도메인 계층에 전달합니다.
 * Architecture Context: UI Layer (ViewModel). Compose 화면(ModelManagementScreen)과 Domain(DownloadModelUseCase)을 연결합니다.
 * Key Flow:
 * 1. 사용자의 다운로드 버튼 클릭 시 `downloadModel` 호출.
 * 2. DownloadModelUseCase의 반환 Flow를 수집하여 UI의 `_downloadState`를 갱신(Idle -> Downloading -> Success/Error).
 * 3. Compose UI는 StateFlow를 관찰하여 다이얼로그 프로그레스 바 갱신.
 */
@HiltViewModel
class ModelManagementViewModel @Inject constructor(
    private val downloadModelUseCase: DownloadModelUseCase
) : ViewModel() {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var downloadJob: kotlinx.coroutines.Job? = null

    /** 진행 중인 다운로드를 취소합니다. 부분 파일(.part)은 다운로드 서비스가 정리합니다. */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = DownloadState.Idle
    }

    fun downloadModel(url: String) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading(0)
            downloadModelUseCase(url).collect { result ->
                when (result) {
                    is AppResult.Success -> {
                        if (result.data == 100) {
                            _downloadState.value = DownloadState.Success
                        } else {
                            _downloadState.value = DownloadState.Downloading(result.data)
                        }
                    }
                    is AppResult.Failure -> {
                        val errorMsg = when (val e = result.error) {
                            is com.kosmos.app.core.common.AppError.NetworkUnavailable -> e.reason
                            else -> e.toString()
                        }
                        _downloadState.value = DownloadState.Error(errorMsg)
                    }
                }
            }
        }
    }

    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }
}
