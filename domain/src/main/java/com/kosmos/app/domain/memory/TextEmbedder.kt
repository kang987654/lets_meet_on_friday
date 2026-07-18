package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult

interface TextEmbedder {
    fun embed(text: String): AppResult<FloatArray>
}
