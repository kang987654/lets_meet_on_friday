package com.localfriday.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_note",
    indices = [
        Index(value = ["createdAt"])
    ]
)
data class KnowledgeEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val sourceSessionId: String?,
    val createdAt: Long
)
