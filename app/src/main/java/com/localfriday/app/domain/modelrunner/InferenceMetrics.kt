package com.localfriday.app.domain.modelrunner

data class InferenceMetrics(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val durationMs: Long,
    val tokensPerSecond: Float,
    val isStreaming: Boolean
)
