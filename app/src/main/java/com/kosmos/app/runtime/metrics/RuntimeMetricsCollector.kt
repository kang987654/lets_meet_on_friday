package com.kosmos.app.runtime.metrics

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.platform.device.TemperatureProvider
import javax.inject.Inject
import javax.inject.Singleton
import com.kosmos.app.domain.audit.AuditTrailService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InferenceMetrics(
    val durationMs: Long,
    val temperatureCelsius: Float,
    val inferenceCount: Int
)


@Singleton
class RuntimeMetricsCollector @Inject constructor(
    private val temperatureProvider: TemperatureProvider,
    private val auditTrailService: AuditTrailService
) {
    // [WHY] LLM 디스패처와 UI 스레드에서 함께 증감되므로 원자적 카운터를 사용한다.
    private val continuousInferenceCount = java.util.concurrent.atomic.AtomicInteger(0)

    // [WHY] SupervisorJob이 없으면 감사 로깅 launch 하나가 실패할 때 스코프 전체가 죽어
    // 이후 모든 발열 감사 이벤트가 무음으로 유실된다.
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    private val _thermalWarning = MutableStateFlow<AppError?>(null)
    val thermalWarning: StateFlow<AppError?> = _thermalWarning.asStateFlow()

    private companion object {
        const val COOLDOWN_MS = 1_000L
    }

    fun checkPreconditions(): AppResult<Unit> {
        val temp = temperatureProvider.getCurrentTemperatureCelsius()
        if (temp >= Constants.THERMAL_SHUTDOWN_CELSIUS) {
            scope.launch {
                auditTrailService.logThermalEvent("system_thermal", "graceful_degradation", temp)
            }
            _thermalWarning.value = AppError.TemperatureCritical(temp)
            // 기존 Hard Halt(에러 반환) 대신 Warning을 반환하여 UI가 경고를 띄울 수 있게 함
            return AppResult.Failure(AppError.TemperatureCritical(temp))
        } else if (temp >= Constants.THERMAL_WARNING_CELSIUS) {
            _thermalWarning.value = AppError.TemperatureWarning(temp)
        } else {
            _thermalWarning.value = null
        }
        return AppResult.Success(Unit)
    }

    fun getCurrentTemp(): Float {
        return temperatureProvider.getCurrentTemperatureCelsius()
    }

    /**
     * 연속 추론이 쌓였고 **기기가 이미 따뜻할 때만** 잠시 쉽니다.
     *
     * [WHY] 예전에는 온도와 무관하게 5번째 추론마다 1초를 무조건 지연했다. 두 가지가 겹쳐
     * 실제 체감 비용이 됐다: ① 툴을 쓰는 대화는 한 번의 사용자 메시지가 **추론 2회**를 쓰므로
     * (호출 턴 + 응답 턴) 사용자 기준으로는 2~3 메시지마다 1초를 냈다. ② 사용자가 생각하는
     * 시간이 끼는 채팅에서 "연속 5회"는 지속 부하와 다르다. 발열 방어는 이미 세 겹으로 있다 —
     * 임계 온도 사전 차단, 경고 온도 이상에서의 토큰 단위 지연, 추론 종료 후 감사 이벤트.
     * 이 지연은 그 위에 얹는 예방책이므로 정말 따뜻할 때만 낸다.
     */
    suspend fun handleCooldownIfNecessary() {
        if (continuousInferenceCount.get() < Constants.THERMAL_COOLDOWN_INFERENCE_COUNT) return
        // [WHY] 카운터는 온도와 무관하게 되돌린다. 여기서 리셋하지 않으면 시원한 기기에서
        // 카운터가 상한에 붙어 버려, 이후 매 턴 온도를 조회하고 조건을 재평가하게 된다.
        continuousInferenceCount.set(0)
        if (temperatureProvider.getCurrentTemperatureCelsius() >= Constants.THERMAL_WARNING_CELSIUS) {
            kotlinx.coroutines.delay(COOLDOWN_MS)
        }
    }

    fun recordStart() {
        continuousInferenceCount.incrementAndGet()
    }

    fun recordEnd(durationMs: Long): AppResult<InferenceMetrics> {
        val temp = temperatureProvider.getCurrentTemperatureCelsius()
        val metrics = InferenceMetrics(durationMs, temp, continuousInferenceCount.get())

        return when {
            temp >= Constants.THERMAL_SHUTDOWN_CELSIUS -> {
                scope.launch {
                    auditTrailService.logThermalEvent("system_thermal", "graceful_degradation", temp)
                }
                _thermalWarning.value = AppError.TemperatureCritical(temp)
                // 기존엔 여기서 중단 요구 이벤트를 보냈으나, 이제는 UI 경고용으로만 쓰임
                AppResult.Failure(AppError.TemperatureCritical(temp))
            }
            temp >= Constants.THERMAL_WARNING_CELSIUS -> {
                scope.launch {
                    auditTrailService.logThermalEvent("system_thermal", "warning", temp)
                }
                _thermalWarning.value = AppError.TemperatureWarning(temp)
                // 경고
                AppResult.Failure(AppError.TemperatureWarning(temp))
            }
            else -> {
                _thermalWarning.value = null
                AppResult.Success(metrics)
            }
        }
    }

}
