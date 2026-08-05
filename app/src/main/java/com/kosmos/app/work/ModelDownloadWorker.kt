package com.kosmos.app.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.mapper.ErrorCode
import com.kosmos.app.core.mapper.ErrorCodeMapper
import com.kosmos.app.core.mapper.ErrorMessages
import com.kosmos.app.domain.modelrunner.ModelLoadManager
import com.kosmos.app.domain.tool.ModelDownloadException
import com.kosmos.app.domain.tool.ModelDownloader
import com.kosmos.app.platform.notification.DownloadNotifier
import com.kosmos.app.platform.notification.NotificationChannels
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * [ModelDownloadWorker]
 * 모델 파일 다운로드를 전경 작업으로 수행하는 WorkManager Worker입니다.
 *
 * ### Architecture Context
 * - **Layer**: App (Work)
 * - **Dependencies**: [ModelDownloader], [ModelLoadManager], [DownloadNotifier]
 *
 * ### Key Flow
 * 1. 전경 서비스로 승격해 진행률 알림을 띄웁니다(실패해도 다운로드는 계속).
 * 2. [ModelDownloader]의 진행 Flow를 수집해 [setProgress]로 UI에 중계합니다.
 * 3. 완료 시 [ModelLoadManager.checkModelFile]로 새 모델 파일을 재탐색시킵니다.
 * 4. 실패는 재시도 가능 여부에 따라 [Result.retry] 또는 [Result.failure]로 나뉩니다.
 *
 * [WHY] 기존에는 ViewModel 스코프에서 다운로드했기 때문에 화면을 벗어나면 3.6GB 전송이
 * 중단됐다. Worker로 옮겨 프로세스 수명과 분리한다. (ADR-006)
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloader: ModelDownloader,
    private val modelLoadManager: ModelLoadManager,
    private val notifier: DownloadNotifier
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        buildForegroundInfo(percent = -1, downloaded = 0L, total = -1L)

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL)
            ?: return Result.failure(workDataOf(KEY_ERROR_CODE to ErrorCode.INVALID_INPUT.name))
        val fileName = inputData.getString(KEY_FILE_NAME)

        promoteToForeground(percent = -1, downloaded = 0L, total = -1L)

        return try {
            var lastReportAt = 0L
            downloader.downloadModel(url, fileName).collect { progress ->
                val now = SystemClock.elapsedRealtime()
                // [WHY] setProgress 는 WorkManager DB 쓰기다. 진행 이벤트마다 호출하면
                // 수 GB 다운로드 동안 디스크 I/O 가 폭주한다.
                if (now - lastReportAt >= PROGRESS_THROTTLE_MS) {
                    lastReportAt = now
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS_PERCENT to progress.percent,
                            KEY_DOWNLOADED_BYTES to progress.downloadedBytes,
                            KEY_TOTAL_BYTES to progress.totalBytes
                        )
                    )
                    promoteToForeground(
                        progress.percent,
                        progress.downloadedBytes,
                        progress.totalBytes
                    )
                }
            }
            // [WHY] 완료 시점을 아는 주체가 ViewModel 이 아니라 Worker 이므로 여기서 재스캔한다.
            // ModelLoadManager 는 @Singleton 이라 UI 가 관찰하는 것과 같은 인스턴스다.
            modelLoadManager.checkModelFile()
            notifier.notifySuccess()
            Result.success()
        } catch (e: ModelDownloadException.InsufficientStorage) {
            val error = AppError.InsufficientStorage(e.requiredBytes)
            notifier.notifyFailure(ErrorMessages.userMessage(error))
            Result.failure(
                workDataOf(
                    KEY_ERROR_CODE to ErrorCode.INSUFFICIENT_STORAGE.name,
                    KEY_REQUIRED_BYTES to e.requiredBytes
                )
            )
        } catch (e: ModelDownloadException.Permanent) {
            failWith(AppError.NetworkUnavailable(e.message ?: "Download failed"))
        } catch (e: ModelDownloadException.Transient) {
            if (runAttemptCount + 1 >= Constants.MODEL_DOWNLOAD_MAX_ATTEMPTS) {
                failWith(AppError.NetworkUnavailable(e.message ?: "Download failed"))
            } else {
                Log.w(TAG, "transient failure, retrying (attempt $runAttemptCount): ${e.message}")
                Result.retry()
            }
        }
        // [WHY] CancellationException 은 잡지 않는다 → 사용자 취소는 WorkManager 가 CANCELLED 로
        // 기록하고, 실패 알림도 띄우지 않으며, 부분 파일은 다운로더가 보존한다.
    }

    private fun failWith(error: AppError): Result {
        notifier.notifyFailure(ErrorMessages.userMessage(error))
        return Result.failure(
            workDataOf(KEY_ERROR_CODE to ErrorCodeMapper.toErrorCode(error).name)
        )
    }

    /**
     * [WHY] 알림 권한이 거부되었거나 API 31+ 의 전경 서비스 시작 제약에 걸리면 setForeground 가
     * 예외를 던진다. 그렇다고 다운로드를 죽이면 사용자는 아무 이유 없이 작업을 잃으므로,
     * 승격 실패는 경고로 강등하고 전송을 계속한다. (ADR-004 의 Graceful Degradation 선례)
     */
    private suspend fun promoteToForeground(percent: Int, downloaded: Long, total: Long) {
        runCatching { setForeground(buildForegroundInfo(percent, downloaded, total)) }
            .onFailure { Log.w(TAG, "foreground promotion failed: ${it.message}") }
    }

    private fun buildForegroundInfo(percent: Int, downloaded: Long, total: Long): ForegroundInfo {
        val cancelIntent = runCatching {
            WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        }.getOrNull()
        val notification = notifier.progressNotification(percent, downloaded, total, cancelIntent)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationChannels.NOTIF_ID_DOWNLOAD_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NotificationChannels.NOTIF_ID_DOWNLOAD_PROGRESS, notification)
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_PROGRESS_PERCENT = "progress_percent"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR_CODE = "error_code"
        const val KEY_REQUIRED_BYTES = "required_bytes"

        private const val TAG = "ModelDownloadWorker"
        private const val PROGRESS_THROTTLE_MS = 500L
    }
}
