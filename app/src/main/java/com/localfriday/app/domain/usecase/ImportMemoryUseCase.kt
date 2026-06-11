package com.localfriday.app.domain.usecase

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.data.local.file.ExportImportManager
import javax.inject.Inject

import com.localfriday.app.domain.assistant.audit.AuditTrailService

class ImportMemoryUseCase @Inject constructor(
    private val exportImportManager: ExportImportManager,
    private val auditTrailService: AuditTrailService
) {
    suspend operator fun invoke(zipUriString: String): AppResult<Unit> {
        val result = exportImportManager.restoreFromZip(zipUriString)
        
        val status = if (result is AppResult.Success) "SUCCESS" else "FAILED"
        auditTrailService.logBackupEvent("system_import", "import", status)
        
        return result
    }
}
