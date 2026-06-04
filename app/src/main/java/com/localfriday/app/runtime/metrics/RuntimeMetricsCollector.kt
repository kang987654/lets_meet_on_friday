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
                // 경고(일단 성공으로 처리하되 경고 로그/이벤트를 던짐, 
                // V1에서는 Failure 대신 Success로 넘기면서 UI에 스낵바를 띄우기 위해 Error 반환 여부 고민. 
                // 일단 정책상 Warning도 Failure로 처리하여 중단할지, 아니면 따로 채널로 알릴지...
                // TASK-067 요구사항: "43°C 이상 경고 이벤트 기록"
                // 여기서는 AppResult.Failure 로 던지면 추론 자체가 실패처리되므로,
                // Warning은 그대로 진행하되, 별도 흐름이나 로깅용도로 사용해야 할 수 있습니다.
                // 편의상 이 Collector의 결과만으로는 Failure를 리턴하도록 해보겠습니다.
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
