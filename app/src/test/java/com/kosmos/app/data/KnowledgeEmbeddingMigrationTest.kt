package com.kosmos.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.core.common.FloatBytes
import com.kosmos.app.data.local.db.KosmosMigrations
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [KnowledgeEmbeddingMigrationTest]
 * v4→v5 마이그레이션이 콤마 구분 임베딩을 BLOB 으로 손실 없이 옮기는지 검증합니다.
 *
 * [WHY] 이 프로젝트에는 마이그레이션 테스트가 **아예 없었다**. 데이터를 재인코딩하는
 * 마이그레이션에 검증이 없으면 사용자 메모리가 조용히 깨진다 — 임베딩이 날아가면 노트는
 * 남지만 벡터 검색에서 영구히 안 보이게 되므로 눈에 띄지도 않는다.
 *
 * [WHY] `MigrationTestHelper` 를 쓰지 않는다. 그것은 내보낸 스키마 JSON 을 **assets 에서만**
 * 읽는데, 스키마는 `:data` 가 생성하고(`room.schemaLocation`) 단위 테스트 assets 는 병합되지
 * 않는다. 스키마 JSON 을 `:app` main assets 로 옮기면 APK 에 실려 나가므로, v4 테이블을 손으로
 * 만들어 마이그레이션 SQL 자체를 태운다.
 *
 * [WHY] Room 의 스키마 검증을 잃는 대신 [마이그레이션 결과 DDL이 v5 스키마와 일치한다] 로
 * 대체한다 — 실제 실패 모드는 "마이그레이션 DDL 이 엔티티 정의와 어긋나는 것"이고, 그것을
 * `sqlite_master` 로 직접 확인하는 것이 더 좁고 확실하다.
 */
@RunWith(RobolectricTestRunner::class)
class KnowledgeEmbeddingMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    /** 4.json 의 `knowledge_note` DDL 그대로. */
    private val v4TableDdl =
        "CREATE TABLE IF NOT EXISTS `knowledge_note` (`id` TEXT NOT NULL, `content` TEXT NOT NULL, " +
            "`sourceSessionId` TEXT, `tags` TEXT NOT NULL, `embedding` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    /** 5.json 의 `knowledge_note` DDL(`${'$'}{TABLE_NAME}` 치환 후). */
    private val v5TableDdl =
        "CREATE TABLE `knowledge_note` (`id` TEXT NOT NULL, `content` TEXT NOT NULL, " +
            "`sourceSessionId` TEXT, `tags` TEXT NOT NULL, `embedding` BLOB, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // 인메모리
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(v4TableDdl)
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

    private fun insertV4Note(id: String, embeddingCsv: String) {
        db.execSQL(
            "INSERT INTO knowledge_note (id, content, sourceSessionId, tags, embedding, createdAt, updatedAt) " +
                "VALUES (?, ?, NULL, ?, ?, ?, ?)",
            arrayOf<Any>(id, "내용-$id", "work", embeddingCsv, 100L, 200L)
        )
    }

    private fun migrate() = KosmosMigrations.MIGRATION_4_5.migrate(db)

    private fun blobOf(id: String): ByteArray? =
        db.query("SELECT embedding FROM knowledge_note WHERE id = ?", arrayOf(id)).use { cursor ->
            cursor.moveToFirst()
            if (cursor.isNull(0)) null else cursor.getBlob(0)
        }

    @Test
    fun `콤마 구분 임베딩이 같은 float 로 디코드되는 BLOB 이 된다`() {
        val original = floatArrayOf(0.5f, -1.25f, 0f, 3.75f)
        insertV4Note("n1", original.joinToString(","))

        migrate()

        assertArrayEquals(original, FloatBytes.decode(blobOf("n1")!!), 0f)
    }

    @Test
    fun `임베딩 외 칼럼은 그대로 보존된다`() {
        insertV4Note("n1", "1.0,2.0")

        migrate()

        db.query("SELECT id, content, sourceSessionId, tags, createdAt, updatedAt FROM knowledge_note").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("n1", cursor.getString(0))
            assertEquals("내용-n1", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertEquals("work", cursor.getString(3))
            assertEquals(100L, cursor.getLong(4))
            assertEquals(200L, cursor.getLong(5))
        }
    }

    @Test
    fun `빈 임베딩 문자열은 null BLOB 이 된다`() {
        insertV4Note("n1", "")

        migrate()

        // [WHY] 빈 배열이 아니라 null 이어야 한다 — "임베딩 없음"과 "길이 0 벡터"를 구분해야
        // searchByVector 가 그 행을 건너뛴다.
        assertNull(blobOf("n1"))
    }

    @Test
    fun `깨진 임베딩 문자열이 있어도 나머지 행은 정상 변환된다`() {
        insertV4Note("n1", "not,a,number")
        insertV4Note("n2", "1.0,2.0")

        migrate()

        assertNull("깨진 행은 null 이어야 한다", blobOf("n1"))
        assertArrayEquals(floatArrayOf(1.0f, 2.0f), FloatBytes.decode(blobOf("n2")!!), 0f)
    }

    @Test
    fun `행이 없어도 마이그레이션이 통과한다`() {
        migrate()

        db.query("SELECT COUNT(*) FROM knowledge_note").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun `마이그레이션 결과 DDL이 v5 스키마와 일치한다`() {
        migrate()

        // [WHY] 이것이 이 테스트의 핵심 계약이다 — 마이그레이션 DDL 이 엔티티 정의와 어긋나면
        // 실제 앱에서 Room 의 스키마 검증이 실행 시점에 실패한다.
        val actual = db.query(
            "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'knowledge_note'"
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }
        assertEquals(normalize(v5TableDdl), normalize(actual))
    }

    @Test
    fun `createdAt 인덱스가 재생성된다`() {
        migrate()

        // [WHY] 테이블을 새로 만들면 인덱스가 함께 사라진다. 빠뜨리면 Room 스키마 검증이 깨진다.
        val count = db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_knowledge_note_createdAt'"
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assertEquals(1, count)
    }

    /**
     * 칼럼 정의만 비교하도록 표면 차이를 걷어낸다.
     *
     * [WHY] `ALTER TABLE ... RENAME` 을 거치면 SQLite 가 저장된 DDL 을 다시 써서 테이블명 인용이
     * 백틱에서 큰따옴표로 바뀌고 괄호 안쪽에 공백이 들어간다. 의미는 같으므로 인용 부호와
     * 공백을 정규화한다 — 비교의 목적은 **칼럼 타입이 v5 스키마와 같은지**다.
     */
    private fun normalize(ddl: String): String = ddl
        .replace("IF NOT EXISTS ", "")
        .replace("`", "")
        .replace("\"", "")
        .replace(Regex("\\s+"), " ")
        .replace("( ", "(")
        .replace(" )", ")")
        .trim()
}
