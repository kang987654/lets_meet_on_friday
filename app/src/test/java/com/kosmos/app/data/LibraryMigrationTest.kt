package com.kosmos.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.data.local.db.KosmosMigrations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [LibraryMigrationTest]
 * v10→v11 마이그레이션(문서 보관함 — 문서·조각·FTS4 색인 테이블, 0.41.0)을 검증합니다.
 *
 * [WHY] 다른 마이그레이션 테스트와 같은 방식(ResultCardMigrationTest) — 마이그레이션 SQL 을 태운 뒤 `sqlite_master` 를 11.json 의 DDL 과
 * 대조한다. FTS4 는 실제로 만들어지고 `matchinfo` 까지 도는지 본다(Room 컴파일 검증은 질의 문법만 본다).
 */
@RunWith(RobolectricTestRunner::class)
class LibraryMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    /** 11.json 이 기대하는 DDL. */
    private val v11 = mapOf(
        "library_document" to "CREATE TABLE `library_document` (`id` TEXT NOT NULL, `uri` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`mimeType` TEXT, `addedAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `chunkCount` INTEGER NOT NULL, " +
            "`charCount` INTEGER NOT NULL, `error` TEXT, PRIMARY KEY(`id`))",
        "library_chunk" to "CREATE TABLE `library_chunk` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `documentId` TEXT NOT NULL, " +
            "`idx` INTEGER NOT NULL, `sectionPath` TEXT NOT NULL, `article` TEXT, `page` INTEGER, `text` TEXT NOT NULL)",
        "library_fts" to "CREATE VIRTUAL TABLE `library_fts` USING FTS4(`tokens` TEXT NOT NULL)"
    )

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `knowledge_note` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        helper.close()
    }

    private fun migrate() = KosmosMigrations.MIGRATION_10_11.migrate(db)

    private fun ddlOf(table: String): String =
        db.query("SELECT sql FROM sqlite_master WHERE name=?", arrayOf(table)).use { cursor ->
            assertTrue("$table 테이블이 없다", cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun normalize(ddl: String): String = ddl
        .replace("`", "")
        .replace("\"", "")
        .replace("IF NOT EXISTS ", "")
        .replace(Regex("\\s+"), " ")
        .trim()

    @Test
    fun `보관함 테이블 셋이 v11 스키마와 일치하고 기존 테이블은 그대로다`() {
        db.execSQL("INSERT INTO knowledge_note (id) VALUES ('k1')")

        migrate()

        v11.forEach { (table, ddl) -> assertEquals(table, normalize(ddl), normalize(ddlOf(table))) }
        db.query("SELECT COUNT(*) FROM knowledge_note").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }

    @Test
    fun `FTS4 색인이 바이그램 토큰을 OR 로 찾고 matchinfo 를 돌려준다`() {
        migrate()
        db.execSQL("INSERT INTO library_fts (rowid, tokens) VALUES (7, '멸시 소멸 시효 효기')")
        db.execSQL("INSERT INTO library_fts (rowid, tokens) VALUES (8, '절도 처벌')")

        db.query("SELECT rowid, matchinfo(library_fts, 'x') FROM library_fts WHERE library_fts MATCH ?", arrayOf("소멸 OR 절도")).use { c ->
            val rows = generateSequence { if (c.moveToNext()) c.getLong(0) to c.getBlob(1).size else null }.toList()
            assertEquals(listOf(7L, 8L), rows.map { it.first }.sorted())
            assertTrue("구 2개 × 정수 3개 × 4바이트", rows.all { it.second == 24 })
        }
    }
}
