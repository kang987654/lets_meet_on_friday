package com.kosmos.app.domain.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 런타임 KV 가드의 판정식(0.41.6 M2) — 경계값과 "모르면 막지 않는다". */
class ToolKvBudgetTest {

    @Test
    fun `결과를 얹어도 천장 안이면 실행하고 넘으면 막는다`() {
        assertTrue("딱 천장", ToolKvBudget.fits(currentKv = 2_828, resultCap = 500, ceiling = 3_328))
        assertFalse("1 토큰 넘음", ToolKvBudget.fits(currentKv = 2_829, resultCap = 500, ceiling = 3_328))
    }

    @Test
    fun `KV 를 모르면 막지 않는다`() {
        assertTrue(ToolKvBudget.fits(currentKv = null, resultCap = 500, ceiling = 3_328))
    }
}
