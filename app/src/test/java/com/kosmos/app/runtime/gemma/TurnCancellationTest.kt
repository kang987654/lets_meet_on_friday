package com.kosmos.app.runtime.gemma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 생성 취소 뒤 모든 입력이 "Task cancelled" 로 끝나던 결함(0.38.6 M0)의 회귀 — 취소가 어느 대화에 닿았는지,
 * 턴 사이의 중지가 쉬는 대화를 건드리지 않는지 고정한다. 네이티브 동작 자체는 exp57.
 */
class TurnCancellationTest {

    private val cancelled = mutableListOf<String>()
    private val gate = TurnCancellation<String> { cancelled += it }

    @Test
    fun `생성 중 취소는 그 대화를 끊고 턴 끝에 취소됐다고 알린다`() {
        gate.begin("chat")
        gate.requestCancel()

        assertEquals(listOf("chat"), cancelled)
        assertTrue(gate.end("chat"))
        assertFalse("다음 턴에 걸어 두지 않는다", gate.consumePending())
    }

    @Test
    fun `취소 없이 끝난 턴은 취소로 보지 않는다`() {
        gate.begin("chat")
        assertFalse(gate.end("chat"))
    }

    @Test
    fun `턴 사이의 중지는 쉬는 대화를 건드리지 않고 다음 채팅 턴에 한 번만 걸린다`() {
        gate.requestCancel()

        assertTrue(cancelled.isEmpty())
        assertTrue(gate.consumePending())
        assertFalse(gate.consumePending())
    }

    @Test
    fun `새 요청은 지난 요청의 늦은 중지를 지운다`() {
        gate.requestCancel()
        gate.clearPending()

        assertFalse(gate.consumePending())
    }

    @Test
    fun `이전 턴의 취소가 다음 턴으로 새지 않는다`() {
        gate.begin("transcribe")
        gate.requestCancel()
        gate.end("transcribe")

        gate.begin("chat")
        assertFalse(gate.end("chat"))
    }
}
