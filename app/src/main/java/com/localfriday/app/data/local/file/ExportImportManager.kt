package com.localfriday.app.data.local.file

import android.content.Context
import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
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
    @ApplicationContext private val context: Context
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
            
            // 3. ZipOutputStream 준비
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
}
