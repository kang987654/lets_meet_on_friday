package com.localfriday.app.domain.modelrunner

import com.localfriday.app.core.common.AppError

sealed class ModelLoadState {
    object Loading : ModelLoadState()
    data class Ready(val modelInfo: ModelInfo) : ModelLoadState()
    data class Error(val error: AppError) : ModelLoadState()
    data class NotFound(val expectedPath: String) : ModelLoadState()
}
