package com.kosmos.app.data

import com.kosmos.app.data.local.db.KosmosDatabase
import com.kosmos.app.data.local.file.isRestorableVersion
import com.kosmos.app.data.local.file.minSupportedSchemaVersion
import com.kosmos.app.data.local.file.replaceDatabaseFiles
import com.kosmos.app.platform.document.readBytesUpTo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * [ExportImportSafetyTest]
 * 백업 복원의 스키마 버전 하한·상한 판정 및 임시 파일을 거치는 원자적 교체(0.40.2 M2-4),
 * 문서 이미지 상한 읽기(0.40.2 M2-6)를 검증합니다.
 */
class ExportImportSafetyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `백업 스키마 버전은 마이그레이션 최소 시작 버전부터 현재 버전까지만 허용한다`() {
        val minVersion = minSupportedSchemaVersion()
        val currentVersion = KosmosDatabase.SCHEMA_VERSION

        assertFalse("마이그레이션 경로가 없는 버전 2 는 거부해야 한다", isRestorableVersion(2))
        assertTrue("최소 지원 버전은 허용해야 한다", isRestorableVersion(minVersion))
        assertTrue("현재 스키마 버전은 허용해야 한다", isRestorableVersion(currentVersion))
        assertFalse("현재보다 높은 버전은 거부해야 한다", isRestorableVersion(currentVersion + 1))
    }

    @Test
    fun `교체 성공 시 대상 DB 파일과 사이드카가 백업 내용으로 바뀐다`() {
        val extractedDir = tempFolder.newFolder("extracted")
        val dbDir = tempFolder.newFolder("db")
        val dbName = "kosmos_db"

        val backupBytes = "new-db-content".toByteArray()
        val backupWalBytes = "new-wal-content".toByteArray()
        File(extractedDir, dbName).writeBytes(backupBytes)
        File(extractedDir, "$dbName-wal").writeBytes(backupWalBytes)

        val dbFile = File(dbDir, dbName).apply { writeBytes("old-db".toByteArray()) }
        val shmFile = File(dbDir, "$dbName-shm").apply { writeBytes("old-shm".toByteArray()) }

        val ok = replaceDatabaseFiles(extractedDir, dbFile, dbName)

        assertTrue(ok)
        assertArrayEquals(backupBytes, dbFile.readBytes())
        assertArrayEquals(backupWalBytes, File(dbDir, "$dbName-wal").readBytes())
        assertFalse("기존 -shm 은 삭제되어야 한다", shmFile.exists())
        assertFalse("임시 파일이 남지 않아야 한다", File(dbDir, "$dbName.restore-tmp").exists())
        assertFalse("임시 wal 파일이 남지 않아야 한다", File(dbDir, "$dbName-wal.restore-tmp").exists())
    }

    @Test
    fun `복사 도중 예외가 나면 원래 DB 파일이 그대로 보존되고 임시 파일이 남지 않는다`() {
        val extractedDir = tempFolder.newFolder("extracted")
        val dbDir = tempFolder.newFolder("db")
        val dbName = "kosmos_db"

        File(extractedDir, dbName).writeBytes("new-db-content".toByteArray())
        val originalBytes = "precious-existing-db".toByteArray()
        val dbFile = File(dbDir, dbName).apply { writeBytes(originalBytes) }

        val ok = replaceDatabaseFiles(extractedDir, dbFile, dbName) { _, dest ->
            dest.writeBytes("partial".toByteArray())
            throw IOException("Disk full")
        }

        assertFalse(ok)
        assertTrue("원래 DB 파일이 살아 있어야 한다", dbFile.exists())
        assertArrayEquals("원래 DB 내용이 손상되지 않아야 한다", originalBytes, dbFile.readBytes())
        assertFalse("임시 파일이 정리되어야 한다", File(dbDir, "$dbName.restore-tmp").exists())
        assertFalse("임시 wal 파일이 정리되어야 한다", File(dbDir, "$dbName-wal.restore-tmp").exists())
    }

    @Test
    fun `readBytesUpTo 는 상한 이하이면 전체 바이트를 반환하고 초과하면 상한+1 바이트만 읽고 null 을 반환한다`() {
        val small = ByteArray(100) { it.toByte() }
        val readSmall = readBytesUpTo( small.inputStream(), maxBytes = 100)
        assertArrayEquals(small, readSmall)

        var bytesRead = 0
        val infiniteStream = object : InputStream() {
            override fun read(): Int {
                bytesRead++
                return 1
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                bytesRead += len
                for (i in 0 until len) b[off + i] = 1
                return len
            }
        }

        val limit = 1024
        val result = readBytesUpTo(infiniteStream, maxBytes = limit)
        assertNull("상한 초과 시 null 이어야 한다", result)
        assertEquals("상한+1 바이트 이상 읽지 않아야 한다", limit + 1, bytesRead)
    }
}
