package com.localfriday.app.domain.modelrunner

import com.localfriday.app.core.common.AppResult
import kotlinx.coroutines.flow.StateFlow

interface ModelRunner {
    val loadState: StateFlow<ModelLoadState>
    
    suspend fun generate(
        prompt: ChatPrompt,
        onToken: ((String) -> Unit)? = null
    ): AppResult<String>

    suspend fun generateWithImage(
        prompt: ChatPrompt,
        imageBytes: ByteArray
    ): AppResult<String>

    suspend fun cancel()
    suspend fun warmUp()
    fun close()
}
