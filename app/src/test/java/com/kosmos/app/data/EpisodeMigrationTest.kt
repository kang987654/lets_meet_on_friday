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
 * [EpisodeMigrationTest]
 * v5→v6 마이그레이션(episode 테이블 신설 + conversation 칼럼 2개)을 검증합니다.
 *
 * [WHY] `MigrationTestHelper` 를 쓰지 않는 이유는 [KnowledgeEmbeddingMigrationTest] 와 같다 —
 * 스키마 JSON 을 assets 에서만 읽는데 스키마는 `:data` 가 생성하고 단위 테스트 assets 는
 * 병합되지 않는다. v5 테이블을 손으로 만들고 마이그레이션 SQL 을 태운 뒤, 결과 DDL 을
 * `sqlite_master` 로 6.json 의 DDL 과 대조한다.
 *
 * [WHY] 기존 데이터 보존도 확인한다 — ALTER ADD COLUMN 이므로 파괴 위험은 낮지만, "기존
 * 행의 새 칼럼이 NULL(=미배정 마커)로 초기화된다"는 것이 catch-up 소급 배정의 전제다.
 */
@RunWith(RobolectricTestRunner::class)
class EpisodeMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    /** 5.json 의 `conversation` DDL 그대로. */
    private val v5ConversationDdl =
        "CREATE TABLE IF NOT EXISTS `conversation` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, " +
            "`role` TEXT NOT NULL, `content` TEXT NOT NULL, `inputType` TEXT NOT NULL, " +
            "`searchUsed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    /** 6.json 의 `episode` DDL(`${'$'}{TABLE_NAME}` 치환 후). */
    private val v6EpisodeDdl =
        "CREATE TABLE `episode` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `status` TEXT NOT NULL, " +
            "`title` TEXT, `summary` TEXT, `tags` TEXT NOT NULL, `startAt` INTEGER NOT NULL, " +
            "`endAt` INTEGER, `messageCount` INTEGER NOT NULL, `retryCount` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    /** 6.json 의 `conversation` DDL(치환 후). */
    private val v6ConversationDdl =
        "CREATE TABLE `conversation` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, " +
            "`role` TEXT NOT NULL, `content` TEXT NOT NULL, `inputType` TEXT NOT NULL, " +
            "`searchUsed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `episodeId` TEXT, " +
            "`recallEpisodeIds` TEXT, PRIMARY KEY(`id`))"

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // 인메모리
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(v5ConversationDdl)
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_sessionId` ON `conversation` (`sessionId`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_createdAt` ON `conversation` (`createdAt`)")
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

    private fun migrate() = KosmosMigrations.MIGRATION_5_6.migrate(db)

    private fun ddlOf(table: String): String =
        db.query("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
            .use { cursor ->
                assertTrue("$table 테이블이 없다", cursor.moveToFirst())
                cursor.getString(0)
            }

    /**
     * 백틱↔큰따옴표·공백·IF NOT EXISTS 차이를 무시하는 정규화 (KnowledgeEmbeddingMigrationTest
     * 와 동일 기준 + 괄호 안쪽 공백 — 마이그레이션 DDL 이 멀티라인 trimIndent 라 `( id` 형태가 남는다).
     */
    private fun normalize(ddl: String): String = ddl
        .replace("`", "")
        .replace("\"", "")
        .replace("IF NOT EXISTS ", "")
        .replace(Regex("\\s+"), " ")
        .replace("( ", "(")
        .replace(" )", ")")
        .trim()

    @Test
    fun `episode 테이블 DDL 이 v6 스키마와 일치한다`() {
        migrate()

        assertEquals(normalize(v6EpisodeDdl), normalize(ddlOf("episode")))
    }

    @Test
    fun `conversation DDL 이 v6 스키마와 일치한다`() {
        migrate()

        assertEquals(normalize(v6ConversationDdl), normalize(ddlOf("conversation")))
    }

    @Test
    fun `인덱스 3개가 생성된다`() {
        // [WHY] 인덱스 누락은 기능이 아니라 Room 스키마 검증 실패(다음 실행 크래시)로 나타난다.
        migrate()

        val names = mutableListOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'index_%'"
        ).use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(0)
        }
        assertTrue("episode.status 인덱스 없음", "index_episode_status" in names)
        assertTrue("episode.createdAt 인덱스 없음", "index_episode_createdAt" in names)
        assertTrue("conversation.episodeId 인덱스 없음", "index_conversation_episodeId" in names)
    }

    @Test
    fun `기존 대화 행이 보존되고 새 칼럼은 NULL 로 초기화된다`() {
        db.execSQL(
            "INSERT INTO conversation (id, sessionId, role, content, inputType, searchUsed, createdAt) " +
                "VALUES ('m1', 's1', 'USER', '안녕', 'TEXT', 0, 100)"
        )

        migrate()

        db.query("SELECT content, episodeId, recallEpisodeIds FROM conversation WHERE id='m1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("안녕", cursor.getString(0))
            // [WHY] NULL = 미배정 마커 — catch-up 소급 배정(getUnassigned)의 전제.
            assertNull(if (cursor.isNull(1)) null else cursor.getString(1))
            assertNull(if (cursor.isNull(2)) null else cursor.getString(2))
        }
    }
}
