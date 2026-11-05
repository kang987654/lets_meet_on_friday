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
 * [MemoryVectorMigrationTest]
 * v12→v13 마이그레이션(0.41.3) — 기억 뜻 벡터 표를 만들고 `knowledge_note` 의 옛 `embedding` 열을 뺀다. `sqlite_master` 를 13.json 의 DDL 과 대조한다.
 */
@RunWith(RobolectricTestRunner::class)
class MemoryVectorMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    /** 13.json 이 기대하는 DDL. */
    private val v13 = mapOf(
        "memory_vector" to "CREATE TABLE `memory_vector` (`kind` TEXT NOT NULL, `refId` TEXT NOT NULL, `modelId` TEXT NOT NULL, " +
            "`vector` BLOB NOT NULL, PRIMARY KEY(`kind`, `refId`))",
        "index_memory_vector_modelId" to "CREATE INDEX `index_memory_vector_modelId` ON `memory_vector` (`modelId`)",
        "knowledge_note" to "CREATE TABLE `knowledge_note` (`id` TEXT NOT NULL, `content` TEXT NOT NULL, `sourceSessionId` TEXT, `tags` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `source` TEXT NOT NULL DEFAULT 'manual', PRIMARY KEY(`id`))",
        "index_knowledge_note_createdAt" to "CREATE INDEX `index_knowledge_note_createdAt` ON `knowledge_note` (`createdAt`)"
    )

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // 12.json 의 knowledge_note
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `knowledge_note` (`id` TEXT NOT NULL, `content` TEXT NOT NULL, `sourceSessionId` TEXT, " +
                                "`tags` TEXT NOT NULL, `embedding` BLOB, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                                "`source` TEXT NOT NULL DEFAULT 'manual', PRIMARY KEY(`id`))"
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_note_createdAt` ON `knowledge_note` (`createdAt`)")
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

    private fun ddlOf(name: String): String =
        db.query("SELECT sql FROM sqlite_master WHERE name=?", arrayOf(name)).use { cursor ->
            assertTrue("$name 이 없다", cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun normalize(ddl: String): String = ddl
        .replace("`", "")
        .replace("\"", "")
        .replace("IF NOT EXISTS ", "")
        .replace(Regex("\\s+"), " ")
        .trim()

    @Test
    fun `벡터 표가 생기고 메모 표는 embedding 열만 빠진 v13 스키마가 된다`() {
        KosmosMigrations.MIGRATION_12_13.migrate(db)

        v13.forEach { (name, ddl) -> assertEquals(name, normalize(ddl), normalize(ddlOf(name))) }
    }

    @Test
    fun `메모의 다른 열과 출처는 그대로 옮겨진다`() {
        db.execSQL(
            "INSERT INTO knowledge_note (id, content, sourceSessionId, tags, embedding, createdAt, updatedAt, source) VALUES " +
                "('k1', '자전거 비밀번호 4821', 's1', '자전거,비밀번호', NULL, 10, 20, 'manual'), ('k2', '땅콩 알레르기', NULL, '', X'0102', 30, 40, 'auto')"
        )

        KosmosMigrations.MIGRATION_12_13.migrate(db)

        db.query("SELECT id, content, sourceSessionId, tags, createdAt, updatedAt, source FROM knowledge_note ORDER BY id").use { c ->
            assertTrue(c.moveToNext())
            assertEquals(listOf("k1", "자전거 비밀번호 4821", "s1", "자전거,비밀번호", "10", "20", "manual"), (0 until 7).map { c.getString(it) })
            assertTrue(c.moveToNext())
            assertEquals(listOf("k2", "땅콩 알레르기", null, "", "30", "40", "auto"), (0 until 7).map { c.getString(it) })
        }
    }
}
