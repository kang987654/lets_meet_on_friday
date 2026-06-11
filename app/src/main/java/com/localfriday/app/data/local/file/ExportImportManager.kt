package com.localfriday.app.data.local.file

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.data.local.db.LocalFridayDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LocalFridayDatabase
) {
    // 임시로 DB 파일의 이름을 상수로 정의 (v1)
    private val dbName = "app_database.db"

    suspend fun createExportZip(manifest: ExportManifest): AppResult<File> = withContext(Dispatchers.IO) {
        try {
            // 1. 저장 공간 여유 확인 (대략적인 10MB 기준)
            val cacheDir = context.cacheDir
            if (cacheDir.usableSpace < 10 * 1024 * 1024) {
                return@withContext AppResult.Failure(AppError.InsufficientStorage(10 * 1024 * 1024L))
            }

            // 2. 임시 ZIP 파일 생성
            val exportFileName = "localfriday_backup_${System.currentTimeMillis()}.zip"
            val zipFile = File(cacheDir, exportFileName)
            
            // 3. DB 안전 동기화 (WAL 모드 플러시)
            database.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)"), null)
            database.close() // 현재 연결을 닫고 안전하게 파일 점유 해제
            
            // 4. ZipOutputStream 준비
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // 3-1. manifest.json 추가
                val manifestJson = Json.encodeToString(manifest)
                val manifestEntry = ZipEntry("manifest.json")
                zos.putNextEntry(manifestEntry)
                zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 3-2. DB 파일 복사
                val dbFile = context.getDatabasePath(dbName)
                if (dbFile.exists()) {
                    addFileToZip(zos, dbFile, dbName)
                }
                
                // Room의 WAL, SHM 파일이 존재한다면 함께 복사해야 DB 무결성 유지 가능
                val walFile = File(dbFile.absolutePath + "-wal")
                if (walFile.exists()) addFileToZip(zos, walFile, "$dbName-wal")
                
                val shmFile = File(dbFile.absolutePath + "-shm")
                if (shmFile.exists()) addFileToZip(zos, shmFile, "$dbName-shm")
            }

            AppResult.Success(zipFile)
        } catch (e: Exception) {
            e.printStackTrace()
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

    suspend fun restoreFromZip(zipUriString: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        val zipUri = android.net.Uri.parse(zipUriString)
        val tempDir = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}")
        try {
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                return@withContext AppResult.Failure(AppError.ImportFailed("Failed to create temp directory for import"))
            }

            // 1. ZIP 파일 압축 해제
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                java.util.zip.ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(tempDir, entry.name)
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return@withContext AppResult.Failure(AppError.ImportFailed("Failed to open zip file"))

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

            // 3. 기존 DB 파일 덮어쓰기
            val dbFile = context.getDatabasePath(dbName)
            val dbDir = dbFile.parentFile ?: return@withContext AppResult.Failure(AppError.ImportFailed("Invalid DB path"))
            if (!dbDir.exists()) dbDir.mkdirs()

            val extractedDb = File(tempDir, dbName)
            if (extractedDb.exists()) {
                extractedDb.copyTo(dbFile, overwrite = true)
            } else {
                return@withContext AppResult.Failure(AppError.ImportSchemaMismatch("Database file not found in the backup."))
            }

            val extractedWal = File(tempDir, "$dbName-wal")
            if (extractedWal.exists()) {
                extractedWal.copyTo(File(dbFile.absolutePath + "-wal"), overwrite = true)
            }

            val extractedShm = File(tempDir, "$dbName-shm")
            if (extractedShm.exists()) {
                extractedShm.copyTo(File(dbFile.absolutePath + "-shm"), overwrite = true)
            }

            AppResult.Success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            AppResult.Failure(AppError.ImportFailed("Import failed: ${e.message}"))
        } finally {
            // 임시 폴더 정리
            tempDir.deleteRecursively()
        }
    }
}
