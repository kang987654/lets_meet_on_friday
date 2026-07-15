package com.kosmos.app.domain.model

sealed class ModelOutput {
    data class TextOutput(
        val content: String
    ) : ModelOutput()
}
