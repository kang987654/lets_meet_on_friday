package com.localfriday.app.domain.usecase

import android.net.Uri
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.data.local.file.ExportImportManager
import javax.inject.Inject

class ImportMemoryUseCase @Inject constructor(
    private val exportImportManager: ExportImportManager
) {
    suspend operator fun invoke(zipUri: Uri): AppResult<Unit> {
        return exportImportManager.restoreFromZip(zipUri)
    }
}
