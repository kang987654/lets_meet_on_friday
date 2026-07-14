package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.MemoryBackupManager
import java.io.File
import javax.inject.Inject

import com.kosmos.app.domain.audit.AuditTrailService

class ExportMemoryUseCase @Inject constructor(
    private val memoryBackupManager: MemoryBackupManager,
    private val auditTrailService: AuditTrailService
) {
    suspend operator fun invoke(): AppResult<File> {
        val result = memoryBackupManager.createExportZip("1.0.0")
        
        val status = if (result is AppResult.Success) "SUCCESS" else "FAILED"
        auditTrailService.logBackupEvent("system_export", "export", status)
        
        return result
    }
}
