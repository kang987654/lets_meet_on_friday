package com.kosmos.app.feature.chat

import com.kosmos.app.core.common.AppError
import com.kosmos.app.domain.modelrunner.ModelInfo
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.runtime.metrics.DeviceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 헤더 캡슐이 별 색의 원인을 말하는가(0.39.3 M3) — 엔진 정지가 발열보다, 원인이 수치보다 먼저. */
class HeaderCapsuleTest {

    private val ready = ModelLoadState.Ready(ModelInfo("mock", "mock", "1.0", "Q4", 0L))
    private val warm = DeviceStatus(temperatureCelsius = 43.4f, appMemoryBytes = 6_500_000_000L)

    @Test
    fun `엔진이 멈췄으면 발열보다 먼저 엔진 오류·모델 없음`() {
        val error = capsuleContent(ModelLoadState.Error(AppError.ModelLoadFailed("x")), warm, heatWarning = true)
        assertEquals("엔진 오류", error?.text)
        assertEquals(CapsuleTone.DANGER, error?.tone)
        assertEquals("모델 없음", capsuleContent(ModelLoadState.NotFound("/m"), warm, heatWarning = false)?.text)
    }

    @Test
    fun `발열 경고면 수치 대신 발열 주의와 온도`() {
        val heat = capsuleContent(ready, warm, heatWarning = true)
        assertEquals("발열 주의 43°", heat?.text)
        assertEquals(CapsuleTone.WARNING, heat?.tone)
        assertEquals("발열 주의", capsuleContent(ready, DeviceStatus(), heatWarning = true)?.text)
    }

    @Test
    fun `평소에는 온도·메모리 수치, 쓸 것이 없으면 캡슐 없음`() {
        assertEquals(CapsuleTone.NORMAL, capsuleContent(ready, warm, heatWarning = false)?.tone)
        assertTrue(capsuleContent(ready, warm, heatWarning = false)!!.text.startsWith("43°"))
        assertNull(capsuleContent(ready, DeviceStatus(), heatWarning = false))
    }

    @Test
    fun `시트의 원인 문장은 시작 화면과 같은 문구이고 엔진이 돌면 없다`() {
        val (title, reason) = engineProblem(ModelLoadState.Error(AppError.ModelLoadFailed("x")))!!
        assertEquals("AI 엔진을 올리지 못했어요", title)
        assertEquals(com.kosmos.app.core.mapper.ErrorMessages.userMessage(AppError.ModelLoadFailed("x")), reason)
        assertNull(engineProblem(ready))
    }
}
