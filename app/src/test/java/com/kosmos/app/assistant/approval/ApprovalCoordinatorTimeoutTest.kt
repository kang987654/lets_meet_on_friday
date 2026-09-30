package com.kosmos.app.assistant.approval

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ApprovalCoordinatorTimeoutTest]
 * 승인 제한 시간의 재시작을 고정합니다 — 캘린더 권한 대화상자가 카드를 가린 동안 흐른 시간 때문에
 * 카드가 뜨자마자 자동 거절되던 문제(2026-09-30 에뮬레이터)의 회귀 방지.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalCoordinatorTimeoutTest {

    private val request = ApprovalRequest(sessionId = "s1", title = "일정 추가", description = "치과 예약")

    @Test
    fun `재시작하면 그 시점부터 제한 시간을 다시 센다`() = runTest {
        val coordinator = ApprovalCoordinator()
        val decision = async { coordinator.requireApproval(request) }
        runCurrent()

        advanceTimeBy(ApprovalCoordinator.APPROVAL_TIMEOUT_MS - 1_000)
        coordinator.restartTimeout()
        advanceTimeBy(ApprovalCoordinator.APPROVAL_TIMEOUT_MS - 1_000)
        runCurrent()
        assertFalse("재시작 후 60초가 안 지났으니 아직 대기 중이어야 한다", decision.isCompleted)

        coordinator.approve()
        assertTrue(decision.await())
    }

    @Test
    fun `재시작이 없으면 여전히 제한 시간 뒤 거절된다`() = runTest {
        val coordinator = ApprovalCoordinator()
        val decision = async { coordinator.requireApproval(request) }
        runCurrent()

        advanceTimeBy(ApprovalCoordinator.APPROVAL_TIMEOUT_MS + 1)
        runCurrent()
        assertTrue(decision.isCompleted)
        assertEquals(false, decision.await())
    }

    @Test
    fun `요청 전에 남은 재시작 신호는 새 요청의 시간을 늘리지 않는다`() = runTest {
        val coordinator = ApprovalCoordinator()
        coordinator.restartTimeout() // 대기 중인 요청이 없을 때의 신호
        val decision = async { coordinator.requireApproval(request) }
        runCurrent()

        advanceTimeBy(ApprovalCoordinator.APPROVAL_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(false, decision.await())
    }
}
