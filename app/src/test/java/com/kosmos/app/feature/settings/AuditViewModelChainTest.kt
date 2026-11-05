package com.kosmos.app.feature.settings

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.audit.AuditHashChain
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AuditViewModelChainTest]
 * 활동 기록 화면의 체인 상태(0.41.4 M3) — 검증 결과를 정상(머리·체인 전 수)·깨짐(번호·이유)·모름으로 옮긴다.
 */
@RunWith(RobolectricTestRunner::class)
class AuditViewModelChainTest {

    private fun repo(result: AppResult<AuditHashChain.Result>) = object : AuditRepository {
        override suspend fun save(event: AuditEvent) = AppResult.Success(Unit)
        override suspend fun getEvents(offset: Int, limit: Int) = AppResult.Success(emptyList<AuditEvent>())
        override suspend fun getEventsBetween(type: AuditEventType, from: Long, to: Long) = AppResult.Success(emptyList<AuditEvent>())
        override suspend fun verifyChain() = result
    }

    private fun stateOf(result: AppResult<AuditHashChain.Result>): AuditChainUi = runBlocking {
        withTimeout(5_000) { AuditViewModel(repo(result)).chain.first { it != AuditChainUi.Checking } }
    }

    @Test
    fun `정상이면 머리 번호와 지문, 체인 전 수를 낸다`() {
        val hash = "a".repeat(64)
        assertEquals(AuditChainUi.Ok(5, 2, 5L to hash), stateOf(AppResult.Success(AuditHashChain.Result.Valid(5, 2, 5L, hash))))
        assertEquals("체인 행이 없으면 머리 없음", AuditChainUi.Ok(0, 3, null), stateOf(AppResult.Success(AuditHashChain.Result.Valid(0, 3, null, null))))
    }

    @Test
    fun `깨졌으면 번호와 이유를 낸다`() {
        assertEquals(
            AuditChainUi.Broken(7, AuditHashChain.Reason.SEQUENCE_GAP),
            stateOf(AppResult.Success(AuditHashChain.Result.Tampered(7, "x", AuditHashChain.Reason.SEQUENCE_GAP)))
        )
    }

    @Test
    fun `검증을 못 하면 경고 대신 모름`() {
        assertEquals(AuditChainUi.Unknown, stateOf(AppResult.Failure(AppError.DbReadError("audit_log"))))
    }

    @Test
    fun `지문은 앞뒤 8자로 줄인다`() {
        assertEquals("01234567…89abcdef", shortHash("01234567" + "x".repeat(48) + "89abcdef"))
        assertEquals("짧음", shortHash("짧음"))
    }
}
