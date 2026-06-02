package com.localfriday.app.domain.modelrunner

import com.localfriday.app.core.common.AppResult
import kotlinx.coroutines.flow.StateFlow

interface ModelRunner {
    val loadState: StateFlow<ModelLoadState>
    
    suspend fun generate(
        prompt: String,
        onToken: ((String) -> Unit)? = null
    ): AppResult<String>

    suspend fun generateWithImage(
        prompt: String,
        imageBytes: ByteArray
    ): AppResult<String>

    suspend fun cancel()
    suspend fun warmUp()
}
