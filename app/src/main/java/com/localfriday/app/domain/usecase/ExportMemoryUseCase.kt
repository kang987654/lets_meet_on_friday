package com.localfriday.app.domain.usecase

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.data.local.file.ExportImportManager
import com.localfriday.app.data.local.file.ExportManifest
import java.io.File
import javax.inject.Inject

class ExportMemoryUseCase @Inject constructor(
    private val exportImportManager: ExportImportManager
) {
    suspend operator fun invoke(): AppResult<File> {
        val manifest = ExportManifest(
            appVersion = "1.0.0" // 추후 실제 BuildConfig 참조 가능
        )
        return exportImportManager.createExportZip(manifest)
    }
}
