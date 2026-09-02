package com.kosmos.app.domain.model

/**
 * 지식 노트 — 가끔-관련 기억(3층 기억 모델의 Knowledge 층). SearchMemory 가 회수한다.
 *
 * [source] 는 "manual"(사용자·툴 콜 저장) / "auto"(C′2 리셋 시점 자동 추출). 자동 항목은
 * 기억 화면에서 출처 배지로 구분되고 삭제할 수 있다 — G3(쓰기=승인) 예외의 통제 장치.
 */
data class KnowledgeNote(
    val id: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val embedding: FloatArray? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val source: String = SOURCE_MANUAL
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_AUTO = "auto"
    }

    // [WHY] data class의 FloatArray는 참조 비교라 구조적 동등성이 깨지므로 contentEquals로 재정의한다.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnowledgeNote) return false
        return id == other.id &&
            content == other.content &&
            tags == other.tags &&
            (embedding?.contentEquals(other.embedding ?: return false) ?: (other.embedding == null)) &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt &&
            source == other.source
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + source.hashCode()
        return result
    }
}
