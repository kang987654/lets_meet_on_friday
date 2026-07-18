package com.kosmos.app.domain.model

data class KnowledgeNote(
    val id: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val embedding: FloatArray? = null,
    val createdAt: Long,
    val updatedAt: Long
)
