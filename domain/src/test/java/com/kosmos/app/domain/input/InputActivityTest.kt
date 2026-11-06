package com.kosmos.app.domain.input

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * [InputActivityTest]
 * 조용히 도는 GPU 일이 시작 전에 보는 공용 입력 판정(0.41.7 M3).
 */
class InputActivityTest {

    @Test
    fun `남은 조용함은 마지막 입력 기준이고 지났으면 0이다`() {
        val input = InputActivity()
        input.mark(1_000)
        assertEquals(5_000L, input.quietRemainingMs(now = 1_000, quietMs = 5_000))
        assertEquals(1_500L, input.quietRemainingMs(now = 4_500, quietMs = 5_000))
        assertEquals(0L, input.quietRemainingMs(now = 9_000, quietMs = 5_000))
    }

    @Test
    fun `입력이 없었으면 바로 돌아온다`() = runBlocking {
        withTimeout(1_000) { InputActivity().awaitQuiet(quietMs = 60_000) }
    }

    @Test
    fun `입력 중에는 기다리고 입력이 당겨지면 곧 돌아온다`() = runBlocking {
        val input = InputActivity()
        input.mark(System.currentTimeMillis() + 60_000)

        val waiting = async { input.awaitQuiet(quietMs = 100) }
        delay(300)
        assertFalse("입력 중에는 돌아오지 않는다", waiting.isCompleted)

        input.mark(0)
        withTimeout(2_000) { waiting.await() }
    }
}
