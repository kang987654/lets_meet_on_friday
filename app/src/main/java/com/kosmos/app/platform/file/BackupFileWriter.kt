package com.kosmos.app.platform.file

import android.content.Context
import android.net.Uri
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackupFileWriter]
 * 내보내기 zip 을 사용자가 SAF 로 고른 위치에 복사합니다.
 *
 * ### Architecture Context
 * - **Layer**: Platform (`:app`)
 *
 * [WHY] `ExportImportManager` 는 zip 을 `cacheDir` 에 만든다 — 사용자가 파일 앱으로 접근할 수
 * 없고 시스템이 언제든 비울 수 있는 위치다. 가져오기 쪽은 이미 `contentResolver` 로 읽고
 * 있으므로(`ExportImportManager.restoreFromZip`), 쓰는 쪽만 대칭으로 여기에 둔다.
 *
 * [WHY] `:data` 의 export 계약(`AppResult<File>`)을 고치지 않는다 — zip 생성과 사용자 저장은
 * 별개 책임이고, 계약을 바꾸면 `ExportMemoryUseCase` 의 감사 로깅까지 번진다.
 */
@Singleton
class BackupFileWriter @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    suspend fun copyTo(source: File, destination: Uri): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val output = context.contentResolver.openOutputStream(destination)
                ?: return@withContext AppResult.Failure(
                    AppError.ExportFailed("저장 위치를 열 수 없습니다")
                )
            output.use { out -> source.inputStream().use { it.copyTo(out) } }
            AppResult.Success(Unit)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(AppError.ExportFailed(e.message ?: "백업 파일 저장 실패"))
        }
    }
}
