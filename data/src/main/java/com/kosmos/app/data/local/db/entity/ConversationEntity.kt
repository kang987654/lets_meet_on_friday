package com.kosmos.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["createdAt"])
    ]
)
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val role: String, // "USER" or "ASSISTANT"
    val content: String,
    val inputType: String, // "TEXT", "VOICE", "IMAGE"
    val searchUsed: Boolean,
    val createdAt: Long
)
