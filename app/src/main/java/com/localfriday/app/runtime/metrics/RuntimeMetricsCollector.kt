package com.localfriday.app.runtime.metrics

import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.platform.device.TemperatureProvider
import javax.inject.Inject
import javax.inject.Singleton
import com.localfriday.app.domain.assistant.audit.AuditTrailService
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
    private var continuousInferenceCount = 0
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

    private val _thermalWarning = MutableStateFlow<AppError?>(null)
    val thermalWarning: StateFlow<AppError?> = _thermalWarning.asStateFlow()

    fun checkPreconditions(): AppResult<Unit> {
        val temp = temperatureProvider.getCurrentTemperatureCelsius()
        if (temp >= 48.0f) {
            scope.launch {
                auditTrailService.logThermalEvent("system_thermal", "graceful_degradation", temp)
            }
            _thermalWarning.value = AppError.TemperatureCritical(temp)
            // 기존 Hard Halt(에러 반환) 대신 Warning을 반환하여 UI가 경고를 띄울 수 있게 함
            return AppResult.Failure(AppError.TemperatureCritical(temp))
        } else if (temp >= 43.0f) {
            _thermalWarning.value = AppError.TemperatureWarning(temp)
        } else {
            _thermalWarning.value = null
        }
        return AppResult.Success(Unit)
    }

    fun getCurrentTemp(): Float {
        return temperatureProvider.getCurrentTemperatureCelsius()
    }

    suspend fun handleCooldownIfNecessary() {
        if (continuousInferenceCount >= 5) {
            kotlinx.coroutines.delay(1000)
            continuousInferenceCount = 0
        }
    }

    fun recordStart() {
        continuousInferenceCount++
    }

    fun recordEnd(durationMs: Long): AppResult<InferenceMetrics> {
        val temp = temperatureProvider.getCurrentTemperatureCelsius()
        val metrics = InferenceMetrics(durationMs, temp, continuousInferenceCount)

        return when {
            temp >= 48.0f -> {
                scope.launch {
                    auditTrailService.logThermalEvent("system_thermal", "graceful_degradation", temp)
                }
                _thermalWarning.value = AppError.TemperatureCritical(temp)
                // 기존엔 여기서 중단 요구 이벤트를 보냈으나, 이제는 UI 경고용으로만 쓰임
                AppResult.Failure(AppError.TemperatureCritical(temp))
            }
            temp >= 43.0f -> {
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

    fun resetCount() {
        continuousInferenceCount = 0
    }
}
