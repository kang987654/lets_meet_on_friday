package com.kosmos.app.work

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.kosmos.app.core.mapper.ErrorCode
import com.kosmos.app.domain.modelrunner.ModelLoadManager
import com.kosmos.app.domain.tool.DownloadProbe
import com.kosmos.app.domain.tool.DownloadProgress
import com.kosmos.app.domain.tool.ModelDownloadException
import com.kosmos.app.domain.tool.ModelDownloader
import com.kosmos.app.platform.notification.DownloadNotifier
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [ModelDownloadWorkerTest]
 * [ModelDownloadWorker]의 예외 분류와 완료 부수효과를 검증합니다.
 *
 * [WHY] 재시도 정책이 틀리면 3.6GB 다운로드가 조용히 포기되거나 무한히 반복된다.
 * [DownloadNotifier]를 인터페이스로 분리해 두었으므로 알림·PendingIntent 없이 검증할 수 있다.
 */
@RunWith(RobolectricTestRunner::class)
class ModelDownloadWorkerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var modelLoadManager: ModelLoadManager
    private lateinit var notifier: DownloadNotifier

    @Before
    fun setUp() {
        modelLoadManager = mockk(relaxed = true)
        notifier = mockk(relaxed = true)
        every { notifier.progressNotification(any(), any(), any(), any()) } returns
            mockk<Notification>(relaxed = true)
    }

    /** 지정한 동작만 하는 최소 다운로더. */
    private fun downloader(
        block: suspend kotlinx.coroutines.flow.FlowCollector<DownloadProgress>.() -> Unit
    ) = object : ModelDownloader {
        override suspend fun probe(url: String) = DownloadProbe(1000L, "etag", true)
        override fun downloadModel(url: String, fileName: String?): Flow<DownloadProgress> =
            flow { block() }
        override fun clearPartial(url: String, fileName: String?) = Unit
        override fun partialBytes(url: String, fileName: String?): Long = 0L
    }

    private fun buildWorker(
        downloader: ModelDownloader,
        attempt: Int = 0,
        url: String? = "https://example.com/model.litertlm"
    ): ModelDownloadWorker =
        TestListenableWorkerBuilder<ModelDownloadWorker>(context)
            .setInputData(
                if (url == null) workDataOf() else workDataOf(ModelDownloadWorker.KEY_URL to url)
            )
            .setRunAttemptCount(attempt)
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: androidx.work.WorkerParameters
                    ) = ModelDownloadWorker(
                        appContext,
                        workerParameters,
                        downloader,
                        modelLoadManager,
                        notifier
                    )
                }
            )
            .build()

    @Test
    fun `정상 완료 시 성공을 반환하고 모델 파일을 재탐색시킨다`() = runBlocking {
        val worker = buildWorker(downloader { emit(DownloadProgress(1000L, 1000L)) })

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        // [WHY] 이 호출이 빠지면 다운로드는 끝났지만 앱이 새 모델을 인식하지 못한다.
        verify(exactly = 1) { modelLoadManager.checkModelFile() }
        verify(exactly = 1) { notifier.notifySuccess() }
    }

    @Test
    fun `일시적 실패는 재시도 한도 안에서 retry를 반환한다`() = runBlocking {
        val worker = buildWorker(
            downloader { throw ModelDownloadException.Transient("connection reset") },
            attempt = 0
        )

        assertTrue(worker.doWork() is ListenableWorker.Result.Retry)
        verify(exactly = 0) { notifier.notifyFailure(any()) }
    }

    @Test
    fun `재시도 한도에 도달한 일시적 실패는 실패로 확정된다`() = runBlocking {
        val worker = buildWorker(
            downloader { throw ModelDownloadException.Transient("connection reset") },
            attempt = 4
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(
            ErrorCode.NETWORK_UNAVAILABLE.name,
            (result as ListenableWorker.Result.Failure).outputData
                .getString(ModelDownloadWorker.KEY_ERROR_CODE)
        )
        verify(exactly = 1) { notifier.notifyFailure(any()) }
    }

    @Test
    fun `영구 실패는 첫 시도에서 곧바로 실패한다`() = runBlocking {
        val worker = buildWorker(
            downloader { throw ModelDownloadException.Permanent("HTTP 404") },
            attempt = 0
        )

        assertTrue(worker.doWork() is ListenableWorker.Result.Failure)
        verify(exactly = 1) { notifier.notifyFailure(any()) }
    }

    @Test
    fun `저장 공간 부족은 실제 필요 바이트 수를 출력에 담는다`() = runBlocking {
        val worker = buildWorker(
            downloader { throw ModelDownloadException.InsufficientStorage(42L, 10L) }
        )

        val result = worker.doWork() as ListenableWorker.Result.Failure

        assertEquals(
            ErrorCode.INSUFFICIENT_STORAGE.name,
            result.outputData.getString(ModelDownloadWorker.KEY_ERROR_CODE)
        )
        // [WHY] 이전 구현은 requiredBytes 를 0L 로 하드코딩해 UI 가 필요 용량을 안내할 수 없었다.
        assertEquals(42L, result.outputData.getLong(ModelDownloadWorker.KEY_REQUIRED_BYTES, -1L))
    }

    @Test
    fun `URL이 없으면 전송을 시도하지 않고 실패한다`() = runBlocking {
        val worker = buildWorker(
            downloader { throw IllegalStateException("should not be called") },
            url = null
        )

        val result = worker.doWork() as ListenableWorker.Result.Failure

        assertEquals(
            ErrorCode.INVALID_INPUT.name,
            result.outputData.getString(ModelDownloadWorker.KEY_ERROR_CODE)
        )
    }
}
