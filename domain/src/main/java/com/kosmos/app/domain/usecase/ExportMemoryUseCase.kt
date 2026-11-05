package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.MemoryBackupManager
import java.io.File
import javax.inject.Inject

/**
 * [ExportMemoryUseCase]
 * 백업 ZIP 을 만들고 활동 기록에 남깁니다.
 *
 * [WHY] **시작 기록을 ZIP 을 만들기 전에** 남긴다(0.41.4) — 예전에는 만든 뒤에 기록해, 내보낸 백업 안의 활동 기록에는 그 내보내기가 없었다.
 * 이제 백업 안 체인의 마지막 줄이 "내보내기 시작"이다. 끝난 결과(성공·실패)는 이 기기의 기록에만 이어진다.
 */
class ExportMemoryUseCase @Inject constructor(
    private val memoryBackupManager: MemoryBackupManager,
    private val auditTrailService: AuditTrailService
) {
    suspend operator fun invoke(): AppResult<File> {
        auditTrailService.logBackupEvent("system_export", "export", STARTED)
        val result = memoryBackupManager.createExportZip("1.0.0")
        auditTrailService.logBackupEvent("system_export", "export", if (result is AppResult.Success) "SUCCESS" else "FAILED")
        return result
    }

    private companion object {
        const val STARTED = "STARTED"
    }
}
