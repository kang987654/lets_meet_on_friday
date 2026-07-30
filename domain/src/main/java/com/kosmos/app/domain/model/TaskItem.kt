package com.kosmos.app.domain.model

// v1 Task/Todo extension
data class TaskItem(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val dueDateIso: String? = null,
    val endDateIso: String? = null,
    val description: String? = null,
    val createdAt: Long
)
