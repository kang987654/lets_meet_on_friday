package com.kosmos.app.assistant.approval

import com.kosmos.app.domain.model.CalendarDraft

/**
 * [ApprovalRequest]
 * 사용자 승인 대기 중인 툴 액션의 표시 정보입니다.
 *
 * @property calendarDraft 캘린더 쓰기 승인일 때 초안 카드 UI(CalendarDraftCard)로 렌더링하기 위한
 *   구조화 페이로드. null이면 일반 승인 시트(ApprovalSheet)로 표시된다. (절충안, 2026-07-31)
 */
data class ApprovalRequest(
    val sessionId: String,
    val title: String,
    val description: String,
    val calendarDraft: CalendarDraft? = null
)
