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
    val remindedAtMs: Long? = null,
    // [WHY] 취소는 삭제가 아니다 — 행이 남아야 채팅 카드가 "취소함"을 보인다(0.38.2). 목록·개수·알람 조회는 cancelledAt IS NULL 만.
    val cancelledAt: Long? = null
)
