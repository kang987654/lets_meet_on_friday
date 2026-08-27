package com.kosmos.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.data.local.db.KosmosMigrations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [ReminderMigrationTest]
 * v6→v7 마이그레이션(task_item 리마인더 칼럼 2개)을 검증합니다.
 *
 * [WHY] `MigrationTestHelper` 를 쓰지 않는 이유는 [EpisodeMigrationTest] 와 같다 — 스키마
 * JSON 을 assets 에서만 읽는데 스키마는 `:data` 가 생성하고 단위 테스트 assets 는 병합되지
 * 않는다. v6 테이블을 손으로 만들고 마이그레이션 SQL 을 태운 뒤 `sqlite_master` 로 대조한다.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    /** 6.json 의 `task_item` DDL 그대로(`${'$'}{TABLE_NAME}` 치환 후). */
    private val v6TaskDdl =
        "CREATE TABLE IF NOT EXISTS `task_item` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
            "`isCompleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER, " +
            "`dueDateIso` TEXT, `endDateIso` TEXT, `description` TEXT, PRIMARY KEY(`id`))"

    /** 7.json 이 기대할 `task_item` DDL — 새 칼럼 2개가 뒤에 붙는다. */
    private val v7TaskDdl =
        "CREATE TABLE `task_item` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
            "`isCompleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER, " +
            "`dueDateIso` TEXT, `endDateIso` TEXT, `description` TEXT, `remindAtIso` TEXT, " +
            "`remindedAtMs` INTEGER, PRIMARY KEY(`id`))"

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // 인메모리
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(v6TaskDdl)
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_item_isCompleted` ON `task_item` (`isCompleted`)")
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

    private fun migrate() = KosmosMigrations.MIGRATION_6_7.migrate(db)

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
    fun `task_item DDL 이 v7 스키마와 일치한다`() {
        migrate()

        assertEquals(normalize(v7TaskDdl), normalize(ddlOf("task_item")))
    }

    @Test
    fun `기존 할 일 행이 보존되고 새 칼럼은 NULL 로 초기화된다`() {
        db.execSQL(
            "INSERT INTO task_item (id, title, isCompleted, createdAt, completedAt, dueDateIso, endDateIso, description) " +
                "VALUES ('t1', '치과 예약', 0, 100, NULL, '2026-08-07T15:00:00', NULL, NULL)"
        )

        migrate()

        db.query("SELECT title, remindAtIso, remindedAtMs FROM task_item WHERE id='t1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("치과 예약", cursor.getString(0))
            // [WHY] NULL = 리마인더 아님 — 기존 할 일·일정이 알림 대상으로 오인되지 않는 전제.
            assertNull(if (cursor.isNull(1)) null else cursor.getString(1))
            assertNull(if (cursor.isNull(2)) null else cursor.getLong(2))
        }
    }
}
