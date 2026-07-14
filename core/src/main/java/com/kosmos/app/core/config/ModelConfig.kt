package com.kosmos.app.core.config

import com.kosmos.app.core.common.Constants

data class ModelConfig(
    val modelId: String = "gemma4-e4b-it",
    val modelFileName: String = Constants.DEFAULT_MODEL_FILENAME,
    val quantization: String = "INT4",
    val maxContextTokens: Int = Constants.MAX_CONTEXT_TOKENS,
    val maxConversationTurns: Int = Constants.MAX_CONVERSATION_TURNS
)
