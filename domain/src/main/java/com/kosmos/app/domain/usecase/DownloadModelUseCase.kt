package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.tool.ModelDownloader
import com.kosmos.app.domain.modelrunner.ModelLoadManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * [DownloadModelUseCase]
 * 핵심 역할: 지정된 URL에서 모델 파일을 다운로드하고 진행 상황을 스트림으로 앱(UI/Domain)에 전달합니다.
 * Architecture Context: Domain Layer (UseCase). UI(ViewModel)와 Data Layer(ModelDownloader)를 연결하며, 완료 시 Runtime Layer(ModelLoadManager)의 상태를 동기화합니다.
 * Key Flow:
 * 1. ModelDownloader를 호출해 0~100까지의 진행률 Flow 획득.
 * 2. 100% 도달 시 ModelLoadManager의 checkModelFile() 트리거.
 * 3. 발생하는 에러를 캐치해 AppError.NetworkUnavailable 등 도메인 에러로 매핑.
 */
class DownloadModelUseCase @Inject constructor(
    private val modelDownloader: ModelDownloader,
    private val modelLoadManager: ModelLoadManager
) {
    /**
     * 모델을 다운로드하고 진행 상태(AppResult<Int>)를 스트림으로 반환합니다.
     * 완료 시 100을 방출하고, 성공적으로 다운로드되면 RuntimeManager를 통해 새 모델을 확인하도록 갱신합니다.
     */
    operator fun invoke(url: String): Flow<AppResult<Int>> {
        return modelDownloader.downloadModel(url)
            .map { progress ->
                if (progress == 100) {
                    // 다운로드 완료 시 모델 파일 재탐색 트리거
                    modelLoadManager.checkModelFile()
                }
                AppResult.Success(progress) as AppResult<Int>
            }
            .catch { e ->
                emit(AppResult.Failure(mapDownloadError(e)))
            }
    }

    // [WHY] 디스크 부족까지 네트워크 오류로 뭉개면 사용자가 잘못된 조치(와이파이 확인 등)를
    // 하게 되므로, 저장 공간 관련 예외는 InsufficientStorage로 구분해 매핑한다.
    private fun mapDownloadError(e: Throwable): AppError {
        val message = e.message ?: "Unknown error"
        return when {
            message.contains("space", ignoreCase = true) ||
                message.contains("ENOSPC", ignoreCase = true) ->
                AppError.InsufficientStorage(0L)
            else -> AppError.NetworkUnavailable("Model download failed: $message")
        }
    }
}
