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
 * [ResultCardMigrationTest]
 * v9→v10 마이그레이션(결과 카드 — `conversation` 결과 칸 3개, `task_item.cancelledAt`)을 검증합니다.
 *
 * [WHY] `MigrationTestHelper` 를 쓰지 않는 이유는 [ReminderMigrationTest] 와 같다 — v9 테이블을 손으로 만들고
 * 마이그레이션 SQL 을 태운 뒤 `sqlite_master` 를 10.json 의 DDL 과 대조한다.
 */
@RunWith(RobolectricTestRunner::class)
class ResultCardMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    /** 9.json 의 DDL 그대로. */
    private val v9ConversationDdl =
        "CREATE TABLE IF NOT EXISTS `conversation` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `role` TEXT NOT NULL, " +
            "`content` TEXT NOT NULL, `inputType` TEXT NOT NULL, `searchUsed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "`episodeId` TEXT, `recallEpisodeIds` TEXT, PRIMARY KEY(`id`))"
    private val v9TaskDdl =
        "CREATE TABLE IF NOT EXISTS `task_item` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `completedAt` INTEGER, `dueDateIso` TEXT, `endDateIso` TEXT, `description` TEXT, " +
            "`remindAtIso` TEXT, `remindedAtMs` INTEGER, PRIMARY KEY(`id`))"

    /** 10.json 이 기대하는 DDL — 새 칼럼이 뒤에 붙는다. */
    private val v10ConversationDdl =
        "CREATE TABLE `conversation` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `role` TEXT NOT NULL, " +
            "`content` TEXT NOT NULL, `inputType` TEXT NOT NULL, `searchUsed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "`episodeId` TEXT, `recallEpisodeIds` TEXT, `resultKind` TEXT, `resultRef` TEXT, `resultMeta` TEXT, PRIMARY KEY(`id`))"
    private val v10TaskDdl =
        "CREATE TABLE `task_item` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `completedAt` INTEGER, `dueDateIso` TEXT, `endDateIso` TEXT, `description` TEXT, " +
            "`remindAtIso` TEXT, `remindedAtMs` INTEGER, `cancelledAt` INTEGER, PRIMARY KEY(`id`))"

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(v9ConversationDdl)
                        db.execSQL(v9TaskDdl)
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

    private fun migrate() = KosmosMigrations.MIGRATION_9_10.migrate(db)

    private fun ddlOf(table: String): String =
        db.query("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor ->
            assertTrue("$table 테이블이 없다", cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun normalize(ddl: String): String = ddl
        .replace("`", "")
        .replace("\"", "")
        .replace("IF NOT EXISTS ", "")
        .replace(Regex("\\s+"), " ")
        .replace("( ", "(")
        .replace(" )", ")")
        .trim()

    @Test
    fun `conversation 과 task_item DDL 이 v10 스키마와 일치한다`() {
        migrate()

        assertEquals(normalize(v10ConversationDdl), normalize(ddlOf("conversation")))
        assertEquals(normalize(v10TaskDdl), normalize(ddlOf("task_item")))
    }

    @Test
    fun `기존 메시지·할 일이 보존되고 새 칼럼은 NULL — 옛 메시지는 결과 없음, 기존 할 일은 취소 안 됨`() {
        db.execSQL(
            "INSERT INTO conversation (id, sessionId, role, content, inputType, searchUsed, createdAt) " +
                "VALUES ('m1', 's1', 'ASSISTANT', '오후 1시 56분에 알려 드릴게요.', 'TEXT', 0, 100)"
        )
        db.execSQL(
            "INSERT INTO task_item (id, title, isCompleted, createdAt, remindAtIso) VALUES ('t1', '물 마시기', 0, 100, '2026-10-15T13:56:00')"
        )

        migrate()

        db.query("SELECT content, resultKind, resultRef, resultMeta FROM conversation WHERE id='m1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("오후 1시 56분에 알려 드릴게요.", cursor.getString(0))
            assertTrue(cursor.isNull(1) && cursor.isNull(2) && cursor.isNull(3))
        }
        db.query("SELECT title, remindAtIso, cancelledAt FROM task_item WHERE id='t1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("물 마시기", cursor.getString(0))
            assertEquals("2026-10-15T13:56:00", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }
    }
}
