package com.kosmos.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_log",
    indices = [
        Index(value = ["eventType"]),
        Index(value = ["timestamp"])
    ]
)
data class AuditEntity(
    @PrimaryKey
    val id: String,
    val eventType: String, // e.g. "MODEL_RUN", "TOOL_CALL"
    val details: String,
    val sessionId: String,
    val timestamp: Long
)
