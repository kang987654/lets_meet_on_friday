package com.kosmos.app.domain.audit

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.memory.MemoryBackupManager
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import com.kosmos.app.domain.usecase.ExportMemoryUseCase
import com.kosmos.app.domain.usecase.ImportMemoryUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [AuditHashChainTest]
 * 활동 기록 해시 체인(0.41.4 M1) — 이어 쓴 체인은 통과하고, 고치기·지우기·끼우기·순서 바꾸기는 처음 깨진 곳에서 잡힌다.
 * 백업 기록 순서(내보내기는 ZIP 전에, 복원 성공은 쓰지 않음)도 함께.
 */
class AuditHashChainTest {

    /** 저장소가 하는 일 — 머리를 보고 번호·앞 지문·지문을 붙인다. */
    private fun chain(vararg details: String, legacy: Int = 0): List<AuditEvent> {
        val out = mutableListOf<AuditEvent>()
        repeat(legacy) { out += AuditEvent("old$it", AuditEventType.TOOL_CALL, "s", "옛 기록 $it", timestamp = it.toLong(), prevHash = AuditHashChain.LEGACY) }
        var prev = AuditHashChain.GENESIS
        details.forEachIndexed { i, d ->
            val base = AuditEvent("e$i", AuditEventType.TOOL_CALL, "s", d, timestamp = 100L, chainSeq = i + 1L, prevHash = prev)
            val hash = AuditHashChain.entryHashOf(base)
            out += base.copy(entryHash = hash)
            prev = hash
        }
        return out
    }

    @Test
    fun `이어 쓴 체인은 통과하고 머리를 알려 준다`() {
        val events = chain("승인", "일정 추가", "메모 검색", legacy = 2)

        val result = AuditHashChain.verify(events) as AuditHashChain.Result.Valid
        assertEquals(3, result.verified)
        assertEquals(2, result.legacy)
        assertEquals(3L, result.headSeq)
        assertEquals(events.last().entryHash, result.headHash)
        assertEquals(64, result.headHash!!.length)
    }

    @Test
    fun `빈 기록은 체인 행 0 건으로 통과한다`() {
        assertEquals(AuditHashChain.Result.Valid(0, 0, null, null), AuditHashChain.verify(emptyList()))
    }

    @Test
    fun `내용을 한 글자 고치면 그 행에서 지문이 안 맞는다`() {
        val events = chain("승인함", "일정 추가", "메모 검색").toMutableList()
        events[0] = events[0].copy(details = "거절함")

        assertEquals(AuditHashChain.Result.Tampered(1, "e0", AuditHashChain.Reason.HASH_MISMATCH), AuditHashChain.verify(events))
    }

    @Test
    fun `가운데 행을 지우면 번호가 건너뛴다`() {
        val events = chain("a", "b", "c").filterNot { it.id == "e1" }

        assertEquals(AuditHashChain.Result.Tampered(3, "e2", AuditHashChain.Reason.SEQUENCE_GAP), AuditHashChain.verify(events))
    }

    @Test
    fun `번호를 맞춰 끼워 넣거나 바꿔치기해도 앞 지문이 안 이어진다`() {
        val events = chain("a", "b", "c").toMutableList()
        // 2번 자리에 다른 행을 지문까지 제대로 계산해 넣었지만 앞 지문은 모른다
        val forged = AuditEvent("x", AuditEventType.TOOL_CALL, "s", "끼운 행", 100L, chainSeq = 2, prevHash = "0".repeat(64))
        events[1] = forged.copy(entryHash = AuditHashChain.entryHashOf(forged))

        assertEquals(AuditHashChain.Result.Tampered(2, "x", AuditHashChain.Reason.LINK_BROKEN), AuditHashChain.verify(events))
    }

    @Test
    fun `체인 행 뒤에 체인 전 행이 오면 순서가 거꾸로다`() {
        val events = chain("a", "b") + AuditEvent("late", AuditEventType.TOOL_CALL, "s", "x", 1L, prevHash = AuditHashChain.LEGACY)

        assertEquals(AuditHashChain.Reason.SEQUENCE_GAP, (AuditHashChain.verify(events) as AuditHashChain.Result.Tampered).reason)
    }

    @Test
    fun `쪽으로 나눠 검증해도 한 번에 한 것과 같다`() {
        val events = chain(*Array(25) { "기록 $it" }, legacy = 3)
        val verifier = AuditHashChain.Verifier()
        events.chunked(7).forEach { assertEquals(null, verifier.accept(it)) }

        assertEquals(AuditHashChain.verify(events), verifier.result())
    }

    @Test
    fun `필드 경계를 옮겨도 같은 지문이 나오지 않는다`() {
        val a = AuditHashChain.entryHash(1, "G", "id", 1, "T", "s|x", "y")
        val b = AuditHashChain.entryHash(1, "G", "id", 1, "T", "s", "x|y")
        assertNotEquals(a, b)
    }

    // ── 백업 기록 순서 ──

    private class FakeAudit : AuditRepository {
        val saved = mutableListOf<AuditEvent>()
        override suspend fun save(event: AuditEvent): AppResult<Unit> = AppResult.Success(Unit).also { saved += event }
        override suspend fun getEvents(offset: Int, limit: Int) = AppResult.Success(emptyList<AuditEvent>())
        override suspend fun getEventsBetween(type: AuditEventType, from: Long, to: Long) = AppResult.Success(emptyList<AuditEvent>())
    }

    private class FakeBackup(private val audit: FakeAudit, private val ok: Boolean) : MemoryBackupManager {
        var auditCountAtZip = -1
        override suspend fun createExportZip(appVersion: String): AppResult<File> {
            auditCountAtZip = audit.saved.size
            return if (ok) AppResult.Success(File("x.zip")) else AppResult.Failure(AppError.ExportFailed("x"))
        }

        override suspend fun restoreFromZip(zipUriString: String): AppResult<Unit> =
            if (ok) AppResult.Success(Unit) else AppResult.Failure(AppError.ImportFailed("x"))
    }

    @Test
    fun `내보내기 시작 기록은 ZIP 을 만들기 전에 남아 백업 안 체인에 든다`() = runBlocking {
        val audit = FakeAudit()
        val backup = FakeBackup(audit, ok = true)

        ExportMemoryUseCase(backup, AuditTrailService(audit))()

        assertEquals("ZIP 을 만들 때 이미 시작 기록이 있다", 1, backup.auditCountAtZip)
        assertEquals(listOf("STARTED", "SUCCESS"), audit.saved.map { it.details.substringAfter(": ") })
        assertTrue(audit.saved.all { it.type == AuditEventType.EXPORT })
    }

    @Test
    fun `복원에 성공하면 바꿔치기한 DB 에 쓰지 않고 실패만 남긴다`() = runBlocking {
        val okAudit = FakeAudit()
        ImportMemoryUseCase(FakeBackup(okAudit, ok = true), AuditTrailService(okAudit))("content://backup")
        assertTrue(okAudit.saved.isEmpty())

        val failAudit = FakeAudit()
        ImportMemoryUseCase(FakeBackup(failAudit, ok = false), AuditTrailService(failAudit))("content://backup")
        assertEquals(listOf("FAILED"), failAudit.saved.map { it.details.substringAfter(": ") })
    }
}
