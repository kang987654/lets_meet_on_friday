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
    val completedAt: Long?,
    val dueDateIso: String? = null,
    val endDateIso: String? = null,
    val description: String? = null,
    // [WHY] 리마인더(B1) = remindAtIso 가 있는 할 일. 발화 여부(remindedAtMs)와
    // 완료(isCompleted)는 별개다 — 알림이 울렸다고 할 일이 끝난 것은 아니다.
    val remindAtIso: String? = null,
    val remindedAtMs: Long? = null
)
