package com.kosmos.app.domain.modelrunner

import com.kosmos.app.core.common.AppError

sealed class ModelLoadState {
    object Loading : ModelLoadState()
    data class FileFound(val modelInfo: ModelInfo) : ModelLoadState()
    object InitializingEngine : ModelLoadState()
    data class Ready(val modelInfo: ModelInfo) : ModelLoadState()
    data class Error(val error: AppError) : ModelLoadState()
    data class NotFound(val expectedPath: String) : ModelLoadState()
}
