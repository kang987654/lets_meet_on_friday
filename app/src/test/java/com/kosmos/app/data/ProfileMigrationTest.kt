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
 * [ProfileMigrationTest]
 * v7→v8 마이그레이션(profile 고정 컬럼 단일 행 → 키-값 행)을 검증합니다.
 *
 * [WHY] DROP+CREATE 방식이라 다른 마이그레이션과 달리 데이터 보존 테스트가 없다 — 보존할
 * 데이터가 구조적으로 존재하지 않는 것(호출처 0곳, 2026-08-15 감사)이 이 방식의 전제다.
 * 대신 다른 테이블이 함께 지워지지 않는 것을 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
class ProfileMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    /** 7.json 의 `profile` DDL 그대로(v2 부터 무변경이던 사문 스키마). */
    private val v7ProfileDdl =
        "CREATE TABLE IF NOT EXISTS `profile` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`style` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    /** 8.json 이 기대할 키-값 DDL. */
    private val v8ProfileDdl =
        "CREATE TABLE `profile` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, " +
            "`source` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))"

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // 인메모리
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(v7ProfileDdl)
                        db.execSQL("CREATE TABLE IF NOT EXISTS `task_item` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, PRIMARY KEY(`id`))")
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

    private fun migrate() = KosmosMigrations.MIGRATION_7_8.migrate(db)

    private fun ddlOf(table: String): String =
        db.query("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
            .use { cursor ->
                assertTrue("$table 테이블이 없다", cursor.moveToFirst())
                cursor.getString(0)
            }

    /** EpisodeMigrationTest 와 동일한 정규화 기준. */
    private fun normalize(ddl: String): String = ddl
        .replace("`", "")
        .replace("\"", "")
        .replace("IF NOT EXISTS ", "")
        .replace(Regex("\\s+"), " ")
        .replace("( ", "(")
        .replace(" )", ")")
        .trim()

    @Test
    fun `profile DDL 이 v8 키-값 스키마와 일치한다`() {
        migrate()

        assertEquals(normalize(v8ProfileDdl), normalize(ddlOf("profile")))
    }

    @Test
    fun `다른 테이블은 건드리지 않는다`() {
        db.execSQL("INSERT INTO task_item (id, title) VALUES ('t1', '치과')")

        migrate()

        db.query("SELECT title FROM task_item WHERE id='t1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("치과", cursor.getString(0))
        }
    }
}
