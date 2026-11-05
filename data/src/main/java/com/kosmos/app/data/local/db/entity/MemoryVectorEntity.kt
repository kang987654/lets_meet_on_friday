package com.kosmos.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 기억(메모·요약된 지난 대화)의 뜻 벡터 (0.41.3) — EmbeddingGemma 2 768차원 int8 + 척도(`Int8Vector.toBytes`).
 *
 * @property kind `note` 또는 `episode`(`MemoryVectorKey.Kind.code`)
 * @property refId `knowledge_note.id` 또는 `episode.id`
 *
 * [WHY] [modelId] 를 함께 둔다 — 모델이 바뀌면 옛 벡터는 다른 좌표계라 섞을 수 없다(`library_vector` 와 같은 이유).
 * [WHY] 외래 키를 걸지 않는다 — 대상 표가 둘이라 걸 수 없고, 메모·대화 저장소가 바뀔 때 그 행을 지운다(무효화).
 */
@Entity(
    tableName = "memory_vector",
    primaryKeys = ["kind", "refId"],
    indices = [Index(value = ["modelId"])]
)
data class MemoryVectorEntity(
    val kind: String,
    val refId: String,
    val modelId: String,
    val vector: ByteArray
) {
    // [WHY] ByteArray 는 내용 비교가 아니다 — 행 식별은 (kind, refId) 로 충분하다.
    override fun equals(other: Any?): Boolean =
        other is MemoryVectorEntity && other.kind == kind && other.refId == refId && other.modelId == modelId

    override fun hashCode(): Int = (kind.hashCode() * 31 + refId.hashCode()) * 31 + modelId.hashCode()
}
