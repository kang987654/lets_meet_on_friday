package com.kosmos.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_item",
    indices = [
        Index(value = ["isCompleted"])
    ]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val createdAt: Long,
    val completedAt: Long?
)
