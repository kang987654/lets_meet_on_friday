package com.localfriday.app.domain.usecase

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.data.local.file.ExportImportManager
import com.localfriday.app.data.local.file.ExportManifest
import java.io.File
import javax.inject.Inject

import com.localfriday.app.assistant.audit.AuditTrailService

class ExportMemoryUseCase @Inject constructor(
    private val exportImportManager: ExportImportManager,
    private val auditTrailService: AuditTrailService
) {
    suspend operator fun invoke(): AppResult<File> {
        val manifest = ExportManifest(
            appVersion = "1.0.0" // 추후 실제 BuildConfig 참조 가능
        )
        val result = exportImportManager.createExportZip(manifest)
        
        val status = if (result is AppResult.Success) "SUCCESS" else "FAILED"
        auditTrailService.logBackupEvent("system_export", "export", status)
        
        return result
    }
}
