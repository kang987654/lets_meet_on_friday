package com.kosmos.app.domain.tool

import com.kosmos.app.core.common.AppError
import kotlinx.coroutines.flow.Flow

/**
 * [ModelDownloadStatus]
 * 프로세스 수명과 무관하게 관찰되는 모델 다운로드 상태입니다.
 *
 * [WHY] 다운로드가 백그라운드 작업으로 이관되면서 "진행 중"만으로는 부족해졌다.
 * 제약(Wi-Fi) 대기와 백오프 대기를 [Queued]로 구분해야 화면이 "멈춘 것처럼 보이는" 상태를
 * 사용자에게 설명할 수 있다.
 */
sealed class ModelDownloadStatus {
    object Idle : ModelDownloadStatus()

    /** Wi-Fi 연결 대기 중이거나 재시도 백오프 대기 중입니다. */
    data class Queued(val attempt: Int) : ModelDownloadStatus()

    data class Running(val progress: DownloadProgress, val attempt: Int) : ModelDownloadStatus()

    object Succeeded : ModelDownloadStatus()

    /** @param resumableBytes 이어받을 수 있는 부분 파일 크기. 0이면 처음부터 다시 받아야 합니다. */
    data class Failed(val error: AppError, val resumableBytes: Long) : ModelDownloadStatus()

    object Cancelled : ModelDownloadStatus()
}

/**
 * [ModelDownloadScheduler]
 * 모델 다운로드를 프로세스 수명과 독립적인 백그라운드 작업으로 예약·관찰하는 계약입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (Tool 인터페이스) — 구현은 `:app`의 `WorkManagerModelDownloadScheduler`
 * - **Dependencies**: 없음 (Pure Kotlin)
 *
 * ### Key Flow
 * 1. [enqueue]가 유니크 작업을 등록합니다(이미 실행/대기 중이면 무시).
 * 2. [status]로 진행률과 종료 결과를 관찰합니다.
 * 3. 사용자가 종료 상태를 확인하면 [acknowledge]로 [ModelDownloadStatus.Idle]로 되돌립니다.
 *
 * [WHY] WorkManager는 Android 타입이므로 인터페이스만 `:domain`에 두고 구현을 `:app`에 남긴다.
 * 덕분에 UseCase와 ViewModel은 계속 `:domain`만 의존한다.
 */
interface ModelDownloadScheduler {

    val status: Flow<ModelDownloadStatus>

    fun enqueue(url: String, fileName: String? = null)

    /** @param deletePartial true면 이어받기용 부분 파일까지 폐기합니다. */
    fun cancel(deletePartial: Boolean = false)

    /** 종료 상태(성공/실패/취소)를 사용자가 확인했음을 표시합니다. */
    fun acknowledge()
}
