package com.kosmos.app.domain.model

data class KnowledgeNote(
    val id: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val embedding: FloatArray? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    // [WHY] data class의 FloatArray는 참조 비교라 구조적 동등성이 깨지므로 contentEquals로 재정의한다.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnowledgeNote) return false
        return id == other.id &&
            content == other.content &&
            tags == other.tags &&
            (embedding?.contentEquals(other.embedding ?: return false) ?: (other.embedding == null)) &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
