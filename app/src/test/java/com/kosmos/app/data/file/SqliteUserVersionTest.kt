package com.kosmos.app.data.file

import com.kosmos.app.data.local.file.readSqliteUserVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [SqliteUserVersionTest]
 * 백업 가져오기의 스키마 버전 검사 입력을 고정합니다 — 더 새 백업을 걸러내지 못하면 다운그레이드
 * 파괴 마이그레이션이 복원한 기억을 조용히 지운다.
 */
class SqliteUserVersionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sqliteHeader(userVersion: Int): File {
        val bytes = ByteArray(100)
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        java.nio.ByteBuffer.wrap(bytes, 60, 4).putInt(userVersion)
        return tmp.newFile().also { it.writeBytes(bytes) }
    }

    @Test
    fun `헤더의 user_version 을 읽는다`() {
        assertEquals(9, readSqliteUserVersion(sqliteHeader(9)))
        assertEquals(12, readSqliteUserVersion(sqliteHeader(12)))
    }

    @Test
    fun `SQLite 파일이 아니거나 짧으면 null`() {
        assertNull(readSqliteUserVersion(tmp.newFile().also { it.writeText("not a database") }))
        assertNull(readSqliteUserVersion(tmp.newFile().also { it.writeBytes(ByteArray(100)) }))
    }
}
