package com.kosmos.app.data.local.file

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.net.toUri
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

    // [WHY] lint(UsableSpace)가 권하는 allocateBytes 는 타 앱 캐시를 지워서 공간을 만든다 —
    // 10MB 백업 ZIP 을 위해 쓸 API 가 아니다. 보수적 사전 점검(있는 공간만 본다)을 유지한다.
    @SuppressLint("UsableSpace")
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
        val zipUri = zipUriString.toUri()
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

            // [WHY] 더 새 앱에서 만든 백업이나 마이그레이션 경로가 없는 너무 옛 백업은 거부한다(0.40.2 M2-4).
            // 새 백업을 그대로 덮어쓰면 다음 실행에서 Room 이 다운그레이드를 만나 fallbackToDestructiveMigrationOnDowngrade(dropAllTables) 로
            // 복원한 기억을 **조용히 전부 지우고**, 최소 지원 버전 미만 백업은 업그레이드 경로가 없어 실행마다 크래시한다.
            val backupVersion = readSqliteUserVersion(extractedDb)
                ?: return@withContext AppResult.Failure(AppError.ImportSchemaMismatch("Backup database header is unreadable."))
            if (backupVersion > KosmosDatabase.SCHEMA_VERSION) {
                return@withContext AppResult.Failure(
                    AppError.ImportSchemaMismatch("Backup schema v$backupVersion is newer than app schema v${KosmosDatabase.SCHEMA_VERSION}.")
                )
            }
            val minVersion = minSupportedSchemaVersion()
            if (backupVersion < minVersion) {
                return@withContext AppResult.Failure(
                    AppError.ImportSchemaMismatch("Backup schema v$backupVersion is older than the oldest supported v$minVersion.")
                )
            }

            // 3. 열려 있는 연결의 WAL 상태를 플러시한 뒤 파일 교체.
            // [WHY] 복원 직후 프로세스 재시작이 전제이므로 여기서 DB를 닫지 않는다.
            database.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")).use { cursor ->
                cursor.moveToFirst()
            }

            val dbFile = context.getDatabasePath(dbName)
            if (!replaceDatabaseFiles(tempDir, dbFile, dbName)) {
                return@withContext AppResult.Failure(AppError.ImportFailed("Database replace failed"))
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

/**
 * SQLite 파일 헤더(오프셋 60, big-endian 4바이트)의 `user_version` 을 읽습니다 — Room 의 스키마 버전.
 *
 * [WHY] DB 를 열지 않고 읽는다 — 가져오기 검증 단계에서 낯선 파일을 SQLite 로 여는 것 자체가
 * 부수효과(저널 생성)를 낼 수 있고, 순수 파일 읽기라 JVM 테스트로 고정할 수 있다.
 * SQLite 매직 헤더가 아니거나 짧으면 null.
 */
fun readSqliteUserVersion(file: File): Int? = runCatching {
    java.io.RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < 100) return@use null
        val magic = ByteArray(16).also { raf.readFully(it) }
        if (!magic.decodeToString().startsWith("SQLite format 3")) return@use null
        raf.seek(60)
        raf.readInt()
    }
}.getOrNull()

/**
 * 마이그레이션 경로가 존재하는 최소 지원 스키마 버전 (0.40.2 M2-4).
 *
 * [WHY] `:data` 에는 테스트 소스셋이 없어 `:app` 의 단위 테스트가 부를 수 있게 [readSqliteUserVersion] 과
 * 마찬가지로 공개 함수로 둔다.
 */
fun minSupportedSchemaVersion(): Int =
    com.kosmos.app.data.local.db.KosmosMigrations.ALL.minOf { it.startVersion }

/** 백업 스키마 버전 [version] 이 현재 앱에서 복원 가능한 범위인지 판정합니다 (0.40.2 M2-4). */
fun isRestorableVersion(version: Int): Boolean =
    version in minSupportedSchemaVersion()..KosmosDatabase.SCHEMA_VERSION

/**
 * 압축 해제된 백업 DB([extractedDir])로 현재 DB([dbFile])를 원자적으로 교체합니다 (0.40.2 M2-4).
 *
 * [WHY] `File.copyTo(target, overwrite = true)` 는 대상을 **먼저 삭제한 뒤** 복사하므로, 복사 도중 디스크 공간 부족 등으로
 * 실패하면 기존 `kosmos_db` 가 사라진 채 끝난다. 같은 폴더의 `.restore-tmp` 임시 파일로 먼저 복사를 마치고 크기까지 확인한 뒤
 * 원자적 이동([moveReplacing])으로 교체해야 실패 시에도 원래 DB 가 온전히 남는다.
 */
fun replaceDatabaseFiles(
    extractedDir: File,
    dbFile: File,
    dbName: String,
    copy: (File, File) -> Unit = { s, d -> s.copyTo(d, overwrite = true) }
): Boolean {
    val dbDir = dbFile.parentFile ?: return false
    if (!dbDir.exists() && !dbDir.mkdirs()) return false

    val extractedDb = File(extractedDir, dbName)
    if (!extractedDb.exists()) return false
    val extractedWal = File(extractedDir, "$dbName-wal")
    val hasWal = extractedWal.exists()

    val tmpDb = File(dbDir, "$dbName.restore-tmp")
    val tmpWal = File(dbDir, "$dbName-wal.restore-tmp")
    tmpDb.delete()
    tmpWal.delete()

    val staged = runCatching {
        copy(extractedDb, tmpDb)
        if (!tmpDb.exists() || tmpDb.length() != extractedDb.length()) return@runCatching false
        if (hasWal) {
            copy(extractedWal, tmpWal)
            if (!tmpWal.exists() || tmpWal.length() != extractedWal.length()) return@runCatching false
        }
        true
    }.getOrDefault(false)

    if (!staged) {
        tmpDb.delete()
        tmpWal.delete()
        return false
    }

    val walTarget = File(dbFile.absolutePath + "-wal")
    val shmTarget = File(dbFile.absolutePath + "-shm")

    if (!moveReplacing(tmpDb, dbFile)) {
        tmpDb.delete()
        tmpWal.delete()
        return false
    }

    if (hasWal) {
        if (!moveReplacing(tmpWal, walTarget)) {
            tmpWal.delete()
            return false
        }
    } else if (walTarget.exists()) {
        walTarget.delete()
    }

    // [WHY] 공유 메모리 색인(-shm)은 복사하지 않고 삭제만 한다 — 다음 연결 때 SQLite 가 재생성한다.
    if (shmTarget.exists()) {
        shmTarget.delete()
    }
    return true
}

/**
 * [source] 를 [target] 자리로 한 번에 옮긴다 — 기존 [target] 은 이동과 함께 대체된다.
 *
 * [WHY] 대상을 먼저 지우고 `renameTo` 하면 그 사이 실패 시 DB 가 없는 채로 끝난다. 원자적 이동은 POSIX `rename`·
 * Windows `MoveFileEx(REPLACE_EXISTING)` 으로 기존 파일을 지우지 않고 바로 대체한다(`File.renameTo` 는 Windows 에서 덮어쓰지 못한다).
 */
private fun moveReplacing(source: File, target: File): Boolean = runCatching {
    java.nio.file.Files.move(
        source.toPath(),
        target.toPath(),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        java.nio.file.StandardCopyOption.ATOMIC_MOVE
    )
}.isSuccess
