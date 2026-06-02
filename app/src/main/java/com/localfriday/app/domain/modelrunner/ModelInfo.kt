package com.localfriday.app.domain.modelrunner

data class ModelInfo(
    val modelId: String,
    val modelPath: String,
    val modelVersion: String,
    val quantization: String,
    val lastLoadedAt: Long
)
