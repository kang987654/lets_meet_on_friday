package com.kosmos.app.domain.model

data class TaskItem(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val dueDateIso: String? = null,
    val endDateIso: String? = null,
    val description: String? = null,
    val createdAt: Long
)
