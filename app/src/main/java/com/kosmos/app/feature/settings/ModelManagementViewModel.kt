package com.kosmos.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.core.mapper.ErrorMessages
import com.kosmos.app.domain.tool.ModelDownloadStatus
import com.kosmos.app.domain.usecase.DownloadModelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * [DownloadState]
 * 모델 다운로드 화면이 표시하는 상태입니다.
 */
sealed class DownloadState {
    object Idle : DownloadState()

    /** Wi-Fi 연결 또는 재시도 백오프를 기다리는 중입니다. */
    object Queued : DownloadState()

    data class Downloading(
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val isRetrying: Boolean
    ) : DownloadState()

    object Success : DownloadState()

    /** @param resumableBytes 0보다 크면 이어받기가 가능합니다. */
    data class Error(val message: String, val resumableBytes: Long) : DownloadState()
}

/**
 * [ModelManagementViewModel]
 * 모델 관리(다운로드) 화면의 상태를 관리하고 사용자 액션을 도메인 계층에 전달합니다.
 *
 * ### Architecture Context
 * - **Layer**: UI (ViewModel)
 * - **Dependencies**: [DownloadModelUseCase]
 *
 * ### Key Flow
 * 1. [DownloadModelUseCase.status]를 UI 상태로 매핑해 노출합니다.
 * 2. [downloadModel]은 백그라운드 작업을 등록만 하고 곧바로 반환합니다.
 * 3. 사용자가 완료/실패 안내를 닫으면 [resetState]로 종료 상태를 소비합니다.
 *
 * [WHY] 이전에는 `viewModelScope`에서 다운로드 Flow를 직접 collect 했기 때문에 화면을
 * 벗어나면 전송이 죽었다. 이제 진행 상태를 "관찰"만 하므로 ViewModel 수명과 무관하다.
 * 중복 실행 방지도 `downloadJob?.isActive` 가드에서 WorkManager 의 유니크 작업(KEEP)으로
 * 옮겨졌다 — 옛 가드는 ViewModel 이 죽으면 함께 사라져 중복을 막지 못했다. (ADR-006)
 */
@HiltViewModel
class ModelManagementViewModel @Inject constructor(
    private val downloadModelUseCase: DownloadModelUseCase
) : ViewModel() {

    val downloadState: StateFlow<DownloadState> = downloadModelUseCase.status
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadState.Idle)

    fun downloadModel(url: String) = downloadModelUseCase.enqueue(url)

    /** 다운로드를 취소하되 이어받기용 부분 파일은 남겨둡니다. */
    fun cancelDownload() = downloadModelUseCase.cancel(deletePartial = false)

    /** 부분 파일까지 폐기해 저장 공간을 회수합니다. */
    fun discardPartial() = downloadModelUseCase.cancel(deletePartial = true)

    fun resetState() = downloadModelUseCase.acknowledge()

    private fun ModelDownloadStatus.toUiState(): DownloadState = when (this) {
        is ModelDownloadStatus.Idle -> DownloadState.Idle
        is ModelDownloadStatus.Queued -> DownloadState.Queued
        is ModelDownloadStatus.Running -> DownloadState.Downloading(
            progress = progress.percent,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
            isRetrying = attempt > 0
        )
        is ModelDownloadStatus.Succeeded -> DownloadState.Success
        is ModelDownloadStatus.Failed -> DownloadState.Error(
            message = ErrorMessages.userMessage(error),
            resumableBytes = resumableBytes
        )
        // [WHY] 사용자가 스스로 취소한 것이므로 오류로 알리지 않고 조용히 초기 상태로 돌아간다.
        is ModelDownloadStatus.Cancelled -> DownloadState.Idle
    }
}
