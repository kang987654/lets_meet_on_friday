package com.kosmos.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.KosmosMigrations
import com.kosmos.app.data.local.repository.AuditRepositoryImpl
import com.kosmos.app.domain.audit.AuditHashChain
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ResetTarget
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import com.kosmos.app.testing.InMemoryKosmosDbRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AuditChainRepositoryTest]
 * 활동 기록 해시 체인(0.41.4 M2) — 실제 Room 위에서 이어 쓰기·검증·변조 감지·동시 저장·조회 순서, v13→v14 마이그레이션, 초기화 기록.
 */
@RunWith(RobolectricTestRunner::class)
class AuditChainRepositoryTest {

    @get:Rule
    val dbRule = InMemoryKosmosDbRule()

    private lateinit var repo: AuditRepositoryImpl

    private fun <T> AppResult<T>.ok(): T {
        assertTrue(toString(), this is AppResult.Success)
        return (this as AppResult.Success).data
    }

    private fun event(id: String, details: String, ts: Long = 100L, type: AuditEventType = AuditEventType.TOOL_CALL) =
        AuditEvent(id, type, "s1", details, ts)

    private fun sql(statement: String, vararg args: Any) = dbRule.db.openHelper.writableDatabase.execSQL(statement, args)

    @Before
    fun setUp() {
        repo = AuditRepositoryImpl(dbRule.db.auditDao())
    }

    @Test
    fun `이어 저장하면 번호가 1부터 붙고 검증을 통과한다`() = runBlocking {
        listOf("승인", "일정 추가", "메모 검색").forEachIndexed { i, d -> repo.save(event("e$i", d)).ok() }

        val result = repo.verifyChain().ok() as AuditHashChain.Result.Valid
        assertEquals(3, result.verified)
        assertEquals(3L, result.headSeq)
        val events = repo.getEvents(0, 10).ok()
        assertEquals("같은 밀리초라도 저장 순서의 반대로", listOf("e2", "e1", "e0"), events.map { it.id })
        assertEquals(AuditHashChain.GENESIS, events.last().prevHash)
        assertEquals(events[1].entryHash, events[0].prevHash)
    }

    @Test
    fun `DB 에서 내용을 고치면 그 행에서 지문이 안 맞는다`() = runBlocking {
        repeat(4) { repo.save(event("e$it", "기록 $it")).ok() }
        sql("UPDATE audit_log SET details = ? WHERE id = ?", "거절함", "e1")

        assertEquals(AuditHashChain.Result.Tampered(2, "e1", AuditHashChain.Reason.HASH_MISMATCH), repo.verifyChain().ok())
    }

    @Test
    fun `DB 에서 행을 지우면 번호가 건너뛴다`() = runBlocking {
        repeat(4) { repo.save(event("e$it", "기록 $it")).ok() }
        sql("DELETE FROM audit_log WHERE id = ?", "e1")

        assertEquals(AuditHashChain.Result.Tampered(3, "e2", AuditHashChain.Reason.SEQUENCE_GAP), repo.verifyChain().ok())
    }

    @Test
    fun `동시에 저장해도 번호가 겹치지 않고 체인이 이어진다`() = runBlocking {
        (0 until 40).map { i -> async(Dispatchers.IO) { repo.save(event("c$i", "동시 $i", ts = 5L)).ok() } }.awaitAll()

        val result = repo.verifyChain().ok() as AuditHashChain.Result.Valid
        assertEquals(40, result.verified)
        assertEquals((1L..40L).toList(), repo.getEvents(0, 100).ok().map { it.chainSeq }.sorted())
    }

    @Test
    fun `체인 전 행은 세기만 하고 뒤에 시각순으로 보인다`() = runBlocking {
        sql("INSERT INTO audit_log (id, eventType, details, sessionId, timestamp) VALUES ('old1', 'TOOL_CALL', 'x', 's', 1)")
        sql("INSERT INTO audit_log (id, eventType, details, sessionId, timestamp) VALUES ('old2', 'TOOL_CALL', 'x', 's', 2)")
        repo.save(event("new", "새 기록", ts = 0L)).ok()

        val result = repo.verifyChain().ok() as AuditHashChain.Result.Valid
        assertEquals(2, result.legacy)
        assertEquals(1, result.verified)
        assertEquals(listOf("new", "old2", "old1"), repo.getEvents(0, 10).ok().map { it.id })
    }

    @Test
    fun `모르는 사건 종류도 저장된 글자 그대로 검증한다`() = runBlocking {
        repo.save(event("e0", "a")).ok()
        // 새 앱이 남긴 종류 — 이 앱은 ERROR 로 읽지만 지문은 원래 글자로 계산돼 있다
        val dao = dbRule.db.auditDao()
        dao.appendChained(com.kosmos.app.data.local.db.entity.AuditEntity("e1", "FUTURE_TYPE", "b", "s1", 100L))
        repo.save(event("e2", "c")).ok()

        assertTrue(repo.verifyChain().ok() is AuditHashChain.Result.Valid)
    }

    @Test
    fun `대화 초기화 뒤 첫 기록은 초기화 사건이고 새 체인이 시작된다`() = runBlocking {
        repeat(3) { repo.save(event("e$it", "기록 $it")).ok() }
        // 뷰모델(DataResetViewModel)이 하는 순서 그대로 — 저장소가 지우고, 성공하면 서비스가 초기화 사건을 남긴다.
        com.kosmos.app.data.local.repository.DataResetRepositoryImpl(dbRule.db).reset(setOf(ResetTarget.CONVERSATIONS)).ok()
        AuditTrailService(repo).logReset(listOf(ResetTarget.CONVERSATIONS.name))

        val events = repo.getEvents(0, 10).ok()
        assertEquals(1, events.size)
        assertEquals(AuditEventType.RESET, events.single().type)
        assertEquals(1L, events.single().chainSeq)
        assertEquals(AuditHashChain.GENESIS, events.single().prevHash)
        assertTrue(repo.verifyChain().ok() is AuditHashChain.Result.Valid)
    }

    // ── v13 → v14 ──

    @Test
    fun `마이그레이션 뒤 체인 열은 기본값이고 색인이 생긴다`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `audit_log` (`id` TEXT NOT NULL, `eventType` TEXT NOT NULL, `details` TEXT NOT NULL, " +
                                "`sessionId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.use { h ->
            val db = h.writableDatabase
            db.execSQL("INSERT INTO audit_log VALUES ('a1', 'TOOL_CALL', 'd', 's', 1)")

            KosmosMigrations.MIGRATION_13_14.migrate(db)

            db.query("SELECT chainSeq, prevHash, entryHash FROM audit_log").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0L, c.getLong(0))
                assertEquals("LEGACY", c.getString(1))
                assertEquals("", c.getString(2))
            }
            db.query("SELECT sql FROM sqlite_master WHERE name = 'index_audit_log_chainSeq'").use { c ->
                assertTrue("색인이 없다", c.moveToFirst())
            }
        }
    }
}
