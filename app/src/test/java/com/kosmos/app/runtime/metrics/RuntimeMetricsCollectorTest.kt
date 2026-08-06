package com.kosmos.app.runtime.metrics

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.platform.device.TemperatureProvider
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RuntimeMetricsCollectorTest]
 * 발열 정책을 검증합니다.
 *
 * [WHY] 이 클래스에는 그동안 테스트가 **0건**이었다. 그런데 온도 임계값과 쿨다운은 모든 추론이
 * 지나는 경로이고, 리터럴(`43.0f`/`48.0f`/`5`)이 `Constants` 의 같은 이름 상수와 **중복**되어
 * 있었다 — 정책을 한 곳에서 바꿀 수 없는 상태였다. 상수로 통일하면서 계약을 고정한다.
 */
class RuntimeMetricsCollectorTest {

    private class FakeTemperature(var celsius: Float) : TemperatureProvider {
        var reads = 0
        override fun getCurrentTemperatureCelsius(): Float {
            reads++
            return celsius
        }
    }

    private fun collector(temp: FakeTemperature): RuntimeMetricsCollector =
        RuntimeMetricsCollector(temp, mockk<AuditTrailService>(relaxed = true))

    // --- 임계값 ---

    @Test
    fun `임계 온도 이상이면 추론을 시작하지 않는다`() {
        val temp = FakeTemperature(Constants.THERMAL_SHUTDOWN_CELSIUS)

        val result = collector(temp).checkPreconditions()

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.TemperatureCritical)
    }

    @Test
    fun `경고 온도에서는 진행하되 경고 상태를 노출한다`() {
        val temp = FakeTemperature(Constants.THERMAL_WARNING_CELSIUS)
        val sut = collector(temp)

        val result = sut.checkPreconditions()

        assertTrue("경고는 중단 사유가 아니다", result is AppResult.Success)
        assertTrue(sut.thermalWarning.value is AppError.TemperatureWarning)
    }

    @Test
    fun `시원해지면 경고 상태가 해제된다`() {
        val temp = FakeTemperature(Constants.THERMAL_WARNING_CELSIUS)
        val sut = collector(temp)
        sut.checkPreconditions()

        temp.celsius = 30f
        sut.checkPreconditions()

        assertEquals(null, sut.thermalWarning.value)
    }

    // --- 쿨다운 ---

    @Test
    fun `시원한 기기에서는 연속 추론이 쌓여도 쉬지 않는다`() = runTest {
        // [WHY] 예전에는 온도와 무관하게 5번째 추론마다 1초를 무조건 지연했다. 툴을 쓰는
        // 대화는 사용자 메시지 하나가 추론 2회를 쓰므로(호출 턴 + 응답 턴) 사용자 기준
        // 2~3 메시지마다 1초를 냈다.
        val temp = FakeTemperature(30f)
        val sut = collector(temp)
        repeat(Constants.THERMAL_COOLDOWN_INFERENCE_COUNT) { sut.recordStart() }

        val elapsed = testScheduler.currentTime
        sut.handleCooldownIfNecessary()

        assertEquals("지연이 없어야 한다", elapsed, testScheduler.currentTime)
    }

    @Test
    fun `따뜻한 기기에서 연속 추론이 쌓이면 쉰다`() = runTest {
        val temp = FakeTemperature(Constants.THERMAL_WARNING_CELSIUS)
        val sut = collector(temp)
        repeat(Constants.THERMAL_COOLDOWN_INFERENCE_COUNT) { sut.recordStart() }

        val before = testScheduler.currentTime
        sut.handleCooldownIfNecessary()

        assertTrue("지연이 있어야 한다", testScheduler.currentTime > before)
    }

    @Test
    fun `상한에 이르지 않으면 온도를 조회조차 하지 않는다`() = runTest {
        // [WHY] 상한 미달에서 조기 반환하지 않으면 매 턴 온도 조회(캐시 만료 시 Binder 왕복)가
        // 추론 경로에 얹힌다.
        val temp = FakeTemperature(50f)
        val sut = collector(temp)
        sut.recordStart()

        sut.handleCooldownIfNecessary()

        assertEquals(0, temp.reads)
    }

    @Test
    fun `쿨다운 판정 뒤 카운터가 리셋되어 다음 판정까지 다시 쌓인다`() = runTest {
        // [WHY] 시원할 때 카운터를 리셋하지 않으면 상한에 붙어 버려 이후 매 턴 온도를 조회한다.
        val temp = FakeTemperature(30f)
        val sut = collector(temp)
        repeat(Constants.THERMAL_COOLDOWN_INFERENCE_COUNT) { sut.recordStart() }
        sut.handleCooldownIfNecessary()
        val readsAfterFirst = temp.reads

        sut.recordStart()
        sut.handleCooldownIfNecessary()

        assertEquals("카운터가 리셋되지 않아 다시 조회했다", readsAfterFirst, temp.reads)
    }
}
