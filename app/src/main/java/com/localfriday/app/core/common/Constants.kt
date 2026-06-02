package com.localfriday.app.core.common

object Constants {
    const val MAX_CONTEXT_TOKENS = 4096
    const val MAX_CONVERSATION_TURNS = 5
    const val MAX_KNOWLEDGE_CONTEXT_ITEMS = 3
    const val MAX_INPUT_CHARS = 8192
    const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024
    const val MAX_IMAGE_DIMENSION_PX = 1024
    const val THERMAL_WARNING_CELSIUS = 43f
    const val THERMAL_SHUTDOWN_CELSIUS = 48f
    const val THERMAL_COOLDOWN_INFERENCE_COUNT = 5
    const val MODEL_DIR_NAME = "models"
    const val DEFAULT_MODEL_FILENAME = "gemma4-e4b-it-q4.litertlm"
    const val CALENDAR_DRAFT_MIN_CONFIDENCE = 0.7f
}
