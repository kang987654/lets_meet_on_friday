package com.localfriday.app.runtime.metrics

import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.platform.device.TemperatureProvider
import javax.inject.Inject
import javax.inject.Singleton

data class InferenceMetrics(
    val durationMs: Long,
    val temperatureCelsius: Float,
    val inferenceCount: Int
)

@Singleton
class RuntimeMetricsCollector @Inject constructor(
    private val temperatureProvider: TemperatureProvider
) {
    private var continuousInferenceCount = 0

    fun checkPreconditions(): AppResult<Unit> {
        val temp = temperatureProvider.getCurrentTemperatureCelsius()
        if (temp >= 48.0f) {
            return AppResult.Failure(AppError.TemperatureCritical(temp))
        }
        return AppResult.Success(Unit)
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
                // 중단 요구 이벤트
                AppResult.Failure(AppError.TemperatureCritical(temp))
            }
            temp >= 43.0f -> {
                // 경고
                AppResult.Failure(AppError.TemperatureWarning(temp))
            }
            else -> {
                AppResult.Success(metrics)
            }
        }
    }

    fun resetCount() {
        continuousInferenceCount = 0
    }
}
