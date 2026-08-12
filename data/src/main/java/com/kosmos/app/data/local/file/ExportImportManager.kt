package com.kosmos.app.data.local.file

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.data.local.db.KosmosDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

import com.kosmos.app.domain.memory.MemoryBackupManager

/**
 * [ExportImportManager]
 * Room DB(지식/일정/감사 데이터)를 Zip으로 백업(Export)하고 복원(Import)하는 관리자입니다.
 *
 * ### Architecture Context
 * - **Layer**: Data (Local / File)
 * - **Dependencies**: [KosmosDatabase], [Context] (파일 경로/ContentResolver)
 *
 * ### Key Flow
 * 1. Export: WAL checkpoint로 DB를 메인 파일에 플러시한 뒤 manifest.json + DB 파일들을 Zip으로 묶습니다.
 * 2. Import: Zip을 임시 폴더에 안전하게 해제(Zip Slip/폭탄 방어) → manifest 검증 → DB 파일 교체 후
 *    백업에 없는 stale -wal/-shm을 제거합니다. 복원 직후 앱 프로세스 재시작이 전제됩니다.
 */
@Singleton
class ExportImportManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: KosmosDatabase
) : MemoryBackupManager {

    private val dbName = Constants.DATABASE_NAME

    override suspend fun createExportZip(appVersion: String): AppResult<File> {
        val manifest = ExportManifest(
            appVersion = appVersion
        )
        return createExportZipInternal(manifest)
    }

    private suspend fun createExportZipInternal(manifest: ExportManifest): AppResult<File> = withContext(Dispatchers.IO) {
        var zipFile: File? = null
        try {
            // 1. 저장 공간 여유 확인 (대략적인 10MB 기준)
            val cacheDir = context.cacheDir
            if (cacheDir.usableSpace < 10 * 1024 * 1024) {
                return@withContext AppResult.Failure(AppError.InsufficientStorage(10 * 1024 * 1024L))
            }

            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) {
                return@withContext AppResult.Failure(AppError.ExportFailed("Database file not found: $dbName"))
            }

            // 2. 임시 ZIP 파일 생성
            val exportFileName = "kosmos_backup_${System.currentTimeMillis()}.zip"
            val currentZip = File(cacheDir, exportFileName)
            zipFile = currentZip

            // 3. DB 안전 동기화 — WAL 내용을 메인 DB 파일로 플러시.
            // [WHY] query()가 돌려주는 Cursor를 소비해야 PRAGMA가 실제 실행되며,
            // 싱글턴 RoomDatabase는 close() 시 재오픈이 불가하므로 절대 닫지 않는다.
            database.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")).use { cursor ->
                cursor.moveToFirst()
            }

            // 4. ZipOutputStream 준비
            ZipOutputStream(FileOutputStream(currentZip)).use { zos ->
                // 4-1. manifest.json 추가
                val manifestJson = Json.encodeToString(manifest)
                val manifestEntry = ZipEntry("manifest.json")
                zos.putNextEntry(manifestEntry)
                zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 4-2. DB 파일 복사
                addFileToZip(zos, dbFile, dbName)

                // checkpoint(TRUNCATE) 이후에도 남아있는 사이드카가 있으면 함께 보존
                val walFile = File(dbFile.absolutePath + "-wal")
                if (walFile.exists()) addFileToZip(zos, walFile, "$dbName-wal")

                val shmFile = File(dbFile.absolutePath + "-shm")
                if (shmFile.exists()) addFileToZip(zos, shmFile, "$dbName-shm")
            }

            AppResult.Success(currentZip)
        } catch (e: kotlinx.coroutines.CancellationException) {
            zipFile?.delete()
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Export failed", e)
            zipFile?.delete()
            AppResult.Failure(AppError.ExportFailed(e.message ?: "Unknown export error"))
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        FileInputStream(file).use { fis ->
            fis.copyTo(zos)
        }
        zos.closeEntry()
    }

    override suspend fun restoreFromZip(zipUriString: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        val zipUri = android.net.Uri.parse(zipUriString)
        val tempDir = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}")
        try {
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                return@withContext AppResult.Failure(AppError.ImportFailed("Failed to create temp directory for import"))
            }

            // 1. ZIP 파일 안전 압축 해제
            val extractResult = extractZipSafely(zipUri, tempDir)
            if (extractResult is AppResult.Failure) {
                return@withContext extractResult
            }

            // 2. Manifest 검증
            val manifestFile = File(tempDir, "manifest.json")
            if (!manifestFile.exists()) {
                return@withContext AppResult.Failure(AppError.ImportSchemaMismatch("manifest.json not found in the backup file."))
            }

            val manifestContent = manifestFile.readText(Charsets.UTF_8)
            val manifest = Json.decodeFromString<ExportManifest>(manifestContent)
            if (manifest.version != "1.0") {
                return@withContext AppResult.Failure(AppError.ImportManifestMismatch("1.0", manifest.version))
            }

            val extractedDb = File(tempDir, dbName)
            if (!extractedDb.exists()) {
                return@withContext AppResult.Failure(AppError.ImportSchemaMismatch("Database file not found in the backup."))
            }

            // 3. 열려 있는 연결의 WAL 상태를 플러시한 뒤 파일 교체.
            // [WHY] 복원 직후 프로세스 재시작이 전제이므로 여기서 DB를 닫지 않는다.
            database.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")).use { cursor ->
                cursor.moveToFirst()
            }

            val dbFile = context.getDatabasePath(dbName)
            val dbDir = dbFile.parentFile ?: return@withContext AppResult.Failure(AppError.ImportFailed("Invalid DB path"))
            if (!dbDir.exists()) dbDir.mkdirs()

            extractedDb.copyTo(dbFile, overwrite = true)

            // 4. 사이드카 교체 — 백업에 없으면 반드시 삭제.
            // [WHY] stale -wal이 남으면 복원된 메인 DB 위에서 옛 트랜잭션이 재생되어 손상된다.
            val walTarget = File(dbFile.absolutePath + "-wal")
            val extractedWal = File(tempDir, "$dbName-wal")
            if (extractedWal.exists()) {
                extractedWal.copyTo(walTarget, overwrite = true)
            } else if (walTarget.exists()) {
                walTarget.delete()
            }

            val shmTarget = File(dbFile.absolutePath + "-shm")
            val extractedShm = File(tempDir, "$dbName-shm")
            if (extractedShm.exists()) {
                extractedShm.copyTo(shmTarget, overwrite = true)
            } else if (shmTarget.exists()) {
                shmTarget.delete()
            }

            AppResult.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Import failed", e)
            AppResult.Failure(AppError.ImportFailed("Import failed: ${e.message}"))
        } finally {
            // 임시 폴더 정리
            tempDir.deleteRecursively()
        }
    }

    /**
     * Zip을 [targetDir] 내부로만 해제합니다. 경로 탈출(Zip Slip) 엔트리와
     * 상한을 초과하는 아카이브(zip-bomb)는 거부합니다.
     */
    private fun extractZipSafely(zipUri: android.net.Uri, targetDir: File): AppResult<Unit> {
        val canonicalTargetPrefix = targetDir.canonicalPath + File.separator
        var totalBytes = 0L
        var entryCount = 0

        val inputStream = context.contentResolver.openInputStream(zipUri)
            ?: return AppResult.Failure(AppError.ImportFailed("Failed to open zip file"))

        inputStream.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) {
                        return AppResult.Failure(AppError.ImportFailed("Backup contains too many entries"))
                    }

                    val outFile = File(targetDir, entry.name)
                    if (!outFile.canonicalPath.startsWith(canonicalTargetPrefix)) {
                        return AppResult.Failure(AppError.ImportFailed("Backup contains an invalid entry path: ${entry.name}"))
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            totalBytes += copyWithLimit(zis, fos, MAX_ZIP_TOTAL_BYTES - totalBytes)
                        }
                        if (totalBytes >= MAX_ZIP_TOTAL_BYTES) {
                            return AppResult.Failure(AppError.ImportFailed("Backup exceeds the maximum allowed size"))
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return AppResult.Success(Unit)
    }

    private fun copyWithLimit(input: java.io.InputStream, output: java.io.OutputStream, remainingBudget: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (copied < remainingBudget) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
        }
        return copied
    }

    private companion object {
        const val TAG = "ExportImportManager"
        const val MAX_ZIP_ENTRIES = 1_000
        const val MAX_ZIP_TOTAL_BYTES = 512L * 1024 * 1024
    }
}
