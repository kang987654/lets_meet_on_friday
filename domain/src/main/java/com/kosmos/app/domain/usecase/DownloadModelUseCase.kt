package com.kosmos.app.domain.usecase

import com.kosmos.app.domain.tool.ModelDownloadScheduler
import com.kosmos.app.domain.tool.ModelDownloadStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * [DownloadModelUseCase]
 * 모델 다운로드를 백그라운드 작업으로 예약하고 그 상태를 관찰·취소하는 유즈케이스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [ModelDownloadScheduler] (구현은 `:app`의 WorkManager 스케줄러)
 *
 * ### Key Flow
 * 1. [enqueue]로 유니크 다운로드 작업을 등록합니다.
 * 2. [status]를 관찰해 대기/진행/완료/실패를 화면에 반영합니다.
 * 3. [acknowledge]로 종료 상태를 소비해, 화면 재진입 때 완료 안내가 반복되지 않게 합니다.
 *
 * [WHY] 예약과 관찰이 서로 다른 시점의 관심사가 되었다 — 작업이 ViewModel보다 오래 살기
 * 때문에 더는 하나의 Flow를 collect하는 구조가 성립하지 않는다. 다만 다루는 대상이 단일
 * 유니크 작업이므로 클래스를 네 개로 쪼개지 않고 하나의 얇은 파사드로 묶는다.
 * 완료 시 모델 파일 재탐색(`checkModelFile`)은 완료 시점을 아는 Worker로 이동했다.
 */
class DownloadModelUseCase @Inject constructor(
    private val scheduler: ModelDownloadScheduler
) {
    val status: Flow<ModelDownloadStatus> get() = scheduler.status

    fun enqueue(url: String, fileName: String? = null) = scheduler.enqueue(url, fileName)

    /** @param deletePartial true면 이어받기용 부분 파일까지 폐기합니다. */
    fun cancel(deletePartial: Boolean = false) = scheduler.cancel(deletePartial)

    fun acknowledge() = scheduler.acknowledge()
}
