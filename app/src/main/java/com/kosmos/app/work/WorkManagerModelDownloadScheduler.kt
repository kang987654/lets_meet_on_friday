package com.kosmos.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.common.ValidationReason
import com.kosmos.app.core.mapper.ErrorCode
import com.kosmos.app.domain.tool.DownloadProgress
import com.kosmos.app.domain.tool.ModelDownloadScheduler
import com.kosmos.app.domain.tool.ModelDownloadStatus
import com.kosmos.app.domain.tool.ModelDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [WorkManagerModelDownloadScheduler]
 * [ModelDownloadScheduler]를 WorkManager 유니크 작업으로 구현합니다.
 *
 * ### Architecture Context
 * - **Layer**: App (Work) — Android 의존성이 있으므로 `:domain`이 아니라 여기 위치합니다.
 * - **Dependencies**: [WorkManager], [ModelDownloader] (부분 파일 조회/삭제용)
 *
 * ### Key Flow
 * 1. [enqueue]가 Wi-Fi 제약과 지수 백오프를 붙인 유니크 작업을 등록합니다.
 * 2. [status]가 `WorkInfo`를 관찰해 도메인 상태로 번역합니다.
 * 3. [acknowledge]가 종료된 작업 id를 기록해 상태를 `Idle`로 되돌립니다.
 */
@Singleton
class WorkManagerModelDownloadScheduler @Inject constructor(
    @ApplicationContext context: Context,
    downloader: ModelDownloader
) : WorkManagerDownloadScheduler(context, downloader, Constants.MODEL_DOWNLOAD_WORK_NAME)

/**
 * [EmbeddingModelDownloadScheduler]
 * 임베딩 모델(선택 설치, 0.41.2) 다운로드 — 채팅 모델과 같은 전경 작업·Wi-Fi 제약·이어받기, 유니크 작업 이름만 다르다.
 *
 * [WHY] 같은 유니크 이름을 쓰면 KEEP 정책 때문에 채팅 모델을 받는 동안 임베딩 모델 요청이 버려지고, 상태 Flow 도 섞인다.
 */
@Singleton
class EmbeddingModelDownloadScheduler @Inject constructor(
    @ApplicationContext context: Context,
    downloader: ModelDownloader
) : WorkManagerDownloadScheduler(context, downloader, Constants.EMBEDDING_DOWNLOAD_WORK_NAME)

/** 유니크 작업 이름 하나에 묶인 다운로드 예약 — [WorkManagerModelDownloadScheduler]·[EmbeddingModelDownloadScheduler] 의 공통 몸체. */
abstract class WorkManagerDownloadScheduler(
    private val context: Context,
    private val downloader: ModelDownloader,
    private val workName: String
) : ModelDownloadScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    /** 사용자가 이미 확인한 종료 작업의 id. */
    private val ackedWorkId = MutableStateFlow<UUID?>(null)

    /** 부분 파일 크기 조회에 필요한 마지막 요청 URL. */
    private val lastRequest = MutableStateFlow<Pair<String, String?>?>(null)

    // [WHY] cancel·acknowledge 는 UI 에서 직접 불린다(ModelManagementViewModel). 예전에는 그 안에서
    // `ListenableFuture.get()`(WorkManager DB 조회)과 부분 파일 삭제를 **메인 스레드**에서 돌렸다.
    // 인터페이스를 suspend 로 바꾸면 호출부·테스트 목이 전부 바뀌므로, 블로킹 부분만 이 IO 스코프로
    // 넘긴다 — 결과는 status Flow(ackedWorkId)로 반영되므로 호출자가 기다릴 필요가 없다.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val status: Flow<ModelDownloadStatus> =
        workManager.getWorkInfosForUniqueWorkFlow(workName)
            .combine(ackedWorkId) { infos, acked ->
                toDownloadStatus(infos.lastOrNull(), acked) { resumableBytes() }
            }
            .distinctUntilChanged()

    override fun enqueue(url: String, fileName: String?) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_URL to url,
                    ModelDownloadWorker.KEY_FILE_NAME to fileName
                )
            )
            .setConstraints(
                Constraints.Builder()
                    // [WHY] 3.6GB 를 이동통신 데이터로 받으면 요금 사고가 된다 — 비과금(Wi-Fi)
                    // 연결에서만 진행한다. 대기 상태는 화면이 "Wi-Fi 대기 중"으로 안내한다.
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        lastRequest.value = url to fileName
        ackedWorkId.value = null
        // [WHY] KEEP: 실행/대기 중인 동일 작업이 있으면 새 요청을 버린다 → 버튼 연타로 3.6GB 를
        // 두 번 받지 않는다. 이전 작업이 이미 종료된 경우엔 KEEP 도 새 작업을 넣으므로
        // 재시도·이어받기 경로는 막히지 않는다.
        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    override fun cancel(deletePartial: Boolean) {
        workManager.cancelUniqueWork(workName)
        if (deletePartial) {
            val request = lastRequest.value ?: return
            ioScope.launch { downloader.clearPartial(request.first, request.second) }
        }
    }

    /**
     * [WHY] 종료된 WorkInfo 는 약 7일간 WorkManager DB 에 남는다. 그대로 두면 화면에 재진입할
     * 때마다 완료/실패 안내가 되살아나므로 확인 시점을 기록해 Idle 로 되돌린다.
     * 메모리에만 두는 이유: 프로세스 사망 후 완료 안내가 한 번 더 뜨는 것은 무해하고,
     * DataStore 로 영속화하면 키와 정리 로직이 늘 뿐 얻는 것이 없다.
     *
     * [WHY] `pruneWork()` 는 부르지 않는다 — 앱 전체의 끝난 작업을 지워, 임베딩 모델 받기를 확인하면 채팅 모델의 실패 정보까지
     * 사라져 "이어받기 가능" 카드가 말없이 받기 버튼으로 바뀌었다(두 스케줄러가 같은 WorkManager 를 쓴다, 0.41.9 외부 분석 W3).
     * 확인은 [ackedWorkId] 로 충분하고, 끝난 정보는 WorkManager 가 기간이 지나면 스스로 지운다.
     */
    override fun acknowledge() {
        ioScope.launch {
            val terminalId = runCatching {
                workManager.getWorkInfosForUniqueWork(workName)
                    .get()
                    ?.lastOrNull()
                    ?.takeIf { it.state.isFinished }
                    ?.id
            }.getOrNull()
            ackedWorkId.value = terminalId
        }
    }

    private fun resumableBytes(): Long =
        lastRequest.value?.let { (url, fileName) -> downloader.partialBytes(url, fileName) } ?: 0L

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}

/**
 * [WorkInfo]를 도메인 상태로 번역합니다.
 *
 * [WHY] WorkManager 없이 단위 테스트할 수 있도록 순수 함수로 분리한다 — 상태 번역이
 * 이 이관에서 가장 실수하기 쉬운 부분이고, 계측 테스트 없이 검증할 수 있어야 한다.
 *
 * @param acknowledgedId 사용자가 이미 확인한 종료 작업의 id. 일치하면 [ModelDownloadStatus.Idle].
 * @param resumableBytes 실패 상태에서만 평가되는 부분 파일 크기 공급자.
 */
internal fun toDownloadStatus(
    info: WorkInfo?,
    acknowledgedId: UUID?,
    resumableBytes: () -> Long
): ModelDownloadStatus {
    if (info == null || info.id == acknowledgedId) return ModelDownloadStatus.Idle
    return when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
            ModelDownloadStatus.Queued(info.runAttemptCount)

        WorkInfo.State.RUNNING -> ModelDownloadStatus.Running(
            progress = info.progress.toDownloadProgress(),
            attempt = info.runAttemptCount
        )

        WorkInfo.State.SUCCEEDED -> ModelDownloadStatus.Succeeded

        WorkInfo.State.FAILED -> ModelDownloadStatus.Failed(
            error = info.outputData.toAppError(),
            resumableBytes = resumableBytes()
        )

        WorkInfo.State.CANCELLED -> ModelDownloadStatus.Cancelled
    }
}

/**
 * [WHY] RUNNING 으로 전이한 직후의 첫 tick 은 progress 가 비어 있다. 0/-1 로 시작해
 * "진행률 미상"으로 표시하면 화면이 0%에 고정된 것처럼 보이지 않는다.
 */
private fun Data.toDownloadProgress(): DownloadProgress = DownloadProgress(
    downloadedBytes = getLong(ModelDownloadWorker.KEY_DOWNLOADED_BYTES, 0L),
    totalBytes = getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, -1L)
)

/**
 * 출력 [Data]에 담긴 [ErrorCode] 이름으로 [AppError]를 복원합니다.
 *
 * [WHY] 렌더된 문자열이 아니라 코드 이름을 실어 보낸다 — 사용자 문구의 단일 출처를
 * `ErrorMessages`로 유지하기 위함이다. 프로세스가 죽어도 실패 사유가 살아남는다.
 */
private fun Data.toAppError(): AppError {
    val code = getString(ModelDownloadWorker.KEY_ERROR_CODE)
        ?.let { name -> ErrorCode.entries.firstOrNull { it.name == name } }
    return when (code) {
        ErrorCode.INSUFFICIENT_STORAGE -> AppError.InsufficientStorage(
            getLong(ModelDownloadWorker.KEY_REQUIRED_BYTES, 0L)
        )
        // [WHY] URL 누락은 사용자 조작이 아니라 호출부 결함이므로 네트워크 문제로 위장하지 않는다.
        ErrorCode.INVALID_INPUT -> AppError.ValidationError("url", ValidationReason.BLANK)
        else -> AppError.NetworkUnavailable("Model download failed")
    }
}
