package com.kosmos.app.domain.model

data class TaskItem(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val dueDateIso: String? = null,
    val endDateIso: String? = null,
    val description: String? = null,
    val createdAt: Long,
    val remindAtIso: String? = null,
    val remindedAtMs: Long? = null,
    /** 취소 시각(0.38.2) — 취소한 리마인더는 행을 남겨 채팅 카드가 "취소함"을 보이고, 할 일 목록·개수·알람 대상에서는 빠진다. */
    val cancelledAt: Long? = null
)
