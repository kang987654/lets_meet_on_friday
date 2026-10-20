package com.kosmos.app.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ProfileRemainingTest]
 * 프로필 시트 "남은 공간 N%" — 토큰 대신 상한 대비 남은 비율, 0..100 으로 묶는다(0.38.3 M5).
 */
class ProfileRemainingTest {
    @Test
    fun `남은 비율은 상한 기준이고 넘쳐도 0 아래로 내려가지 않는다`() {
        assertEquals(83, remainingPercent(17, max = 100))
        assertEquals(100, remainingPercent(0, max = 100))
        assertEquals(0, remainingPercent(116, max = 100))
    }
}
