package com.kosmos.app.work

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.mapper.ErrorCode
import com.kosmos.app.domain.tool.ModelDownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * [DownloadStatusMappingTest]
 * `WorkInfo` → [ModelDownloadStatus] 번역 규칙을 검증합니다.
 *
 * [WHY] 상태 번역은 이 이관에서 가장 실수하기 쉬운 지점이며(대기/재시도/종료 잔류),
 * 순수 함수로 분리해 두었으므로 WorkManager 런타임 없이 전 상태를 검증할 수 있다.
 */
class DownloadStatusMappingTest {

    private val workId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun workInfo(
        state: WorkInfo.State,
        progress: Data = Data.EMPTY,
        output: Data = Data.EMPTY,
        attempt: Int = 0,
        id: UUID = workId
    ) = WorkInfo(
        id = id,
        state = state,
        tags = emptySet(),
        progress = progress,
        outputData = output,
        runAttemptCount = attempt,
        generation = 0
    )

    @Test
    fun `작업이 없으면 Idle이다`() {
        assertEquals(ModelDownloadStatus.Idle, toDownloadStatus(null, null) { 0L })
    }

    @Test
    fun `이미 확인한 종료 작업은 Idle로 되돌아간다`() {
        val info = workInfo(WorkInfo.State.SUCCEEDED)
        // 확인 전에는 성공 상태가 그대로 노출된다.
        assertEquals(ModelDownloadStatus.Succeeded, toDownloadStatus(info, null) { 0L })
        // 확인 후에는 화면 재진입 시 완료 안내가 되살아나지 않는다.
        assertEquals(ModelDownloadStatus.Idle, toDownloadStatus(info, workId) { 0L })
    }

    @Test
    fun `ENQUEUED와 BLOCKED는 대기 상태로 매핑된다`() {
        assertEquals(
            ModelDownloadStatus.Queued(0),
            toDownloadStatus(workInfo(WorkInfo.State.ENQUEUED), null) { 0L }
        )
        assertEquals(
            ModelDownloadStatus.Queued(2),
            toDownloadStatus(workInfo(WorkInfo.State.BLOCKED, attempt = 2), null) { 0L }
        )
    }

    @Test
    fun `RUNNING은 진행 데이터를 그대로 옮긴다`() {
        val info = workInfo(
            WorkInfo.State.RUNNING,
            progress = workDataOf(
                ModelDownloadWorker.KEY_DOWNLOADED_BYTES to 500L,
                ModelDownloadWorker.KEY_TOTAL_BYTES to 1000L
            ),
            attempt = 1
        )
        val status = toDownloadStatus(info, null) { 0L } as ModelDownloadStatus.Running
        assertEquals(500L, status.progress.downloadedBytes)
        assertEquals(1000L, status.progress.totalBytes)
        assertEquals(50, status.progress.percent)
        assertEquals(1, status.attempt)
    }

    @Test
    fun `RUNNING 첫 tick은 진행 데이터가 비어 있어 진행률 미상으로 표시된다`() {
        val status = toDownloadStatus(workInfo(WorkInfo.State.RUNNING), null) { 0L }
            as ModelDownloadStatus.Running
        assertEquals(0L, status.progress.downloadedBytes)
        assertEquals(-1L, status.progress.totalBytes)
        // -1 은 "퍼센트를 알 수 없음"이므로 화면이 0%에 고정된 것처럼 보이지 않는다.
        assertEquals(-1, status.progress.percent)
    }

    @Test
    fun `CANCELLED는 취소 상태로 매핑된다`() {
        assertEquals(
            ModelDownloadStatus.Cancelled,
            toDownloadStatus(workInfo(WorkInfo.State.CANCELLED), null) { 0L }
        )
    }

    @Test
    fun `FAILED는 출력 ErrorCode로 AppError를 복원하고 이어받기 크기를 담는다`() {
        val info = workInfo(
            WorkInfo.State.FAILED,
            output = workDataOf(
                ModelDownloadWorker.KEY_ERROR_CODE to ErrorCode.INSUFFICIENT_STORAGE.name,
                ModelDownloadWorker.KEY_REQUIRED_BYTES to 4_000_000_000L
            )
        )
        val status = toDownloadStatus(info, null) { 123L } as ModelDownloadStatus.Failed
        val error = status.error as AppError.InsufficientStorage
        assertEquals(4_000_000_000L, error.requiredBytes)
        assertEquals(123L, status.resumableBytes)
    }

    @Test
    fun `출력에 ErrorCode가 없으면 네트워크 오류로 수렴한다`() {
        val status = toDownloadStatus(workInfo(WorkInfo.State.FAILED), null) { 0L }
            as ModelDownloadStatus.Failed
        assertTrue(status.error is AppError.NetworkUnavailable)
    }

    @Test
    fun `URL 누락 실패는 네트워크 문제로 위장하지 않는다`() {
        val info = workInfo(
            WorkInfo.State.FAILED,
            output = workDataOf(ModelDownloadWorker.KEY_ERROR_CODE to ErrorCode.INVALID_INPUT.name)
        )
        val status = toDownloadStatus(info, null) { 0L } as ModelDownloadStatus.Failed
        assertTrue(status.error is AppError.ValidationError)
    }

    @Test
    fun `다른 작업의 확인 기록은 현재 작업을 숨기지 않는다`() {
        val info = workInfo(WorkInfo.State.SUCCEEDED, id = workId)
        val otherId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        assertEquals(ModelDownloadStatus.Succeeded, toDownloadStatus(info, otherId) { 0L })
    }
}
