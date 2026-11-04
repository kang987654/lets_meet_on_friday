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
 * [LibraryVectorMigrationTest]
 * v11→v12 마이그레이션(보관함 조각의 뜻 벡터 표, 0.41.2)을 검증합니다 — `sqlite_master` 를 12.json 의 DDL 과 대조(LibraryMigrationTest 와 같은 방식).
 */
@RunWith(RobolectricTestRunner::class)
class LibraryVectorMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    /** 12.json 이 기대하는 DDL. */
    private val v12 = mapOf(
        "library_vector" to "CREATE TABLE `library_vector` (`chunkRowId` INTEGER NOT NULL, `documentId` TEXT NOT NULL, " +
            "`modelId` TEXT NOT NULL, `vector` BLOB NOT NULL, PRIMARY KEY(`chunkRowId`))",
        "index_library_vector_documentId" to "CREATE INDEX `index_library_vector_documentId` ON `library_vector` (`documentId`)",
        "index_library_vector_modelId" to "CREATE INDEX `index_library_vector_modelId` ON `library_vector` (`modelId`)"
    )

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `library_document` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))")
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
    fun `벡터 표와 색인이 v12 스키마와 일치하고 기존 표는 그대로다`() {
        db.execSQL("INSERT INTO library_document (id) VALUES ('d1')")

        KosmosMigrations.MIGRATION_11_12.migrate(db)

        v12.forEach { (name, ddl) -> assertEquals(name, normalize(ddl), normalize(ddlOf(name))) }
        db.query("SELECT COUNT(*) FROM library_document").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
}
