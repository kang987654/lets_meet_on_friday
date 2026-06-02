package com.localfriday.app.domain.model

data class KnowledgeNote(
    val id: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
)
