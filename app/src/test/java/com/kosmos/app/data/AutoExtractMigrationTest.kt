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
 * [AutoExtractMigrationTest]
 * v8→v9 마이그레이션(knowledge_note.source 컬럼 + profile_suggestion 테이블)을 검증합니다.
 *
 * [WHY] 기존 지식 행이 `source='manual'` 로 채워지는 것이 핵심 — 자동 추출 이전의 모든 기억은
 * 사용자·툴 콜이 저장한 것이므로 "자동" 배지가 붙으면 거짓 표시다. DDL 은 9.json 이 기대할
 * 문구와 정규화 비교한다(ProfileMigrationTest 와 같은 기준).
 */
@RunWith(RobolectricTestRunner::class)
class AutoExtractMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    /** 8.json 의 `knowledge_note` DDL 그대로. */
    private val v8KnowledgeDdl =
        "CREATE TABLE IF NOT EXISTS `knowledge_note` (`id` TEXT NOT NULL, `content` TEXT NOT NULL, " +
            "`sourceSessionId` TEXT, `tags` TEXT NOT NULL, `embedding` BLOB, `createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    /** 9.json 이 기대할 `profile_suggestion` DDL. */
    private val v9SuggestionDdl =
        "CREATE TABLE `profile_suggestion` (`id` TEXT NOT NULL, `key` TEXT NOT NULL, `value` TEXT NOT NULL, " +
            "`episodeId` TEXT, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`))"

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // 인메모리
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(v8KnowledgeDdl)
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

    private fun migrate() = KosmosMigrations.MIGRATION_8_9.migrate(db)

    private fun ddlOf(table: String): String =
        db.query("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
            .use { cursor ->
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
    fun `기존 지식 행은 source='manual' 로 보존된다`() {
        db.execSQL(
            "INSERT INTO knowledge_note (id, content, sourceSessionId, tags, embedding, createdAt, updatedAt) " +
                "VALUES ('k1', '자물쇠 비밀번호 4936', NULL, '비밀번호', NULL, 1, 1)"
        )

        migrate()

        db.query("SELECT content, source FROM knowledge_note WHERE id='k1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("자물쇠 비밀번호 4936", cursor.getString(0))
            assertEquals("manual", cursor.getString(1))
        }
    }

    @Test
    fun `knowledge_note DDL 에 NOT NULL DEFAULT 'manual' 컬럼이 붙는다`() {
        migrate()

        val ddl = normalize(ddlOf("knowledge_note"))
        assertTrue(ddl, ddl.contains("source TEXT NOT NULL DEFAULT 'manual'"))
    }

    @Test
    fun `profile_suggestion 테이블과 status 인덱스가 생긴다`() {
        migrate()

        assertEquals(normalize(v9SuggestionDdl), normalize(ddlOf("profile_suggestion")))
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_profile_suggestion_status'"
        ).use { cursor -> assertTrue("status 인덱스가 없다", cursor.moveToFirst()) }
    }

    @Test
    fun `마이그레이션은 두 번 적용해도 안전하다`() {
        // [WHY] CREATE ... IF NOT EXISTS 계약 — 강제종료 후 재실행 등으로 부분 적용된 DB 에서
        // 테이블 생성이 예외를 내지 않아야 한다. (ALTER 는 Room 이 버전으로 1회만 보장한다.)
        migrate()
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `profile_suggestion` (`id` TEXT NOT NULL, `key` TEXT NOT NULL, " +
                "`value` TEXT NOT NULL, `episodeId` TEXT, `status` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        assertEquals(normalize(v9SuggestionDdl), normalize(ddlOf("profile_suggestion")))
    }
}
