package com.kosmos.app.assistant.approval

import com.kosmos.app.domain.model.CalendarDraft

/**
 * [ApprovalRequest]
 * 사용자 승인 대기 중인 툴 액션의 표시 정보입니다.
 *
 * @property calendarDraft 캘린더 쓰기 승인 — 화면이 이 시점에 캘린더 권한을 요청하는 신호다. 시트 표시는 [preview] 가 맡는다(0.38.2 M5).
 * @property showsNotification 승인하면 나중에 알림이 떠야 하는 액션(리마인더) — 화면이 알림 권한을 이 시점에 요청한다(0.36.0 D4).
 * @property preview 공통 승인 시트가 구조화해 보일 내용(무엇을 · 언제 · 메모). null 이면 [title]·[description] 만 보인다.
 */
data class ApprovalRequest(
    val sessionId: String,
    val title: String,
    val description: String,
    val calendarDraft: CalendarDraft? = null,
    val showsNotification: Boolean = false,
    val preview: ApprovalPreview? = null
)

/**
 * 승인 시트에 보일 구조화 내용 — 실행기가 검증을 마친 인자로 만든다. 시각은 ISO 원문, 표기는 화면이 한다(§4-7).
 *
 * @property subject 일정 제목·알림 내용·저장할 메모.
 * @property atIso 시작·알림 시각. @property endIso 일정 끝 시각. @property note 일정 메모.
 */
data class ApprovalPreview(
    val kind: Kind,
    val subject: String,
    val atIso: String? = null,
    val endIso: String? = null,
    val note: String? = null
) {
    enum class Kind { SCHEDULE, REMINDER, MEMORY }
}
