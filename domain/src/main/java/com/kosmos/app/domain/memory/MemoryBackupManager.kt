package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import java.io.File

interface MemoryBackupManager {
    suspend fun createExportZip(appVersion: String): AppResult<File>
    suspend fun restoreFromZip(zipUriString: String): AppResult<Unit>
}
