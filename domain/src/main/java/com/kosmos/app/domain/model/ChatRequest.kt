package com.kosmos.app.domain.model

data class ChatRequest(
    val sessionId: String,
    val content: String,
    val inputType: InputType,
    val imageBytes: ByteArray? = null,
    val source: String = "USER"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChatRequest

        if (sessionId != other.sessionId) return false
        if (content != other.content) return false
        if (inputType != other.inputType) return false
        if (imageBytes != null) {
            if (other.imageBytes == null) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false
        } else if (other.imageBytes != null) return false
        if (source != other.source) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + inputType.hashCode()
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + source.hashCode()
        return result
    }
}
