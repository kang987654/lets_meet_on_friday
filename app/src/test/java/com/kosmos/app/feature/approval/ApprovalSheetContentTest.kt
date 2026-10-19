package com.kosmos.app.feature.approval

import com.kosmos.app.assistant.approval.ApprovalPreview
import com.kosmos.app.assistant.approval.ApprovalRequest
import com.kosmos.app.domain.model.ProfileSuggestion
import com.kosmos.app.domain.model.ProfileSuggestionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 공통 승인 시트(0.38.2 M5)가 보일 내용 — 종류별 대상·언제·버튼 글자, ISO 원문을 노출하지 않는다(§4-7). 날짜·시간대 고정(§2-④).
 */
class ApprovalSheetContentTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 15)
    private fun request(preview: ApprovalPreview?) = ApprovalRequest("s1", "제목", "설명", preview = preview)

    @Test
    fun `일정 — 오늘이면 '오늘', 끝 시각은 큰 시각 아래 '까지', 메모는 메모 면, 주 버튼은 추가하기`() {
        val c = ApprovalSheetContent.of(
            request(ApprovalPreview(ApprovalPreview.Kind.SCHEDULE, "팀 회의", "2026-10-15T15:00:00", "2026-10-15T16:00:00", "3층 회의실")),
            today, zone
        )
        assertEquals("일정 추가", c.label)
        assertEquals("팀 회의", c.title)
        assertEquals("오후 3:00", c.value)
        assertEquals("오늘", c.sub)
        assertEquals("오후 4:00까지", c.valueSub)
        assertEquals("3층 회의실", c.note)
        assertEquals("추가하기", c.approveLabel)
        assertEquals("취소", c.rejectLabel)
    }

    @Test
    fun `알림 — 다른 날이면 날짜 표기, 메모는 큰 값 없이 내용만`() {
        val reminder = ApprovalSheetContent.of(request(ApprovalPreview(ApprovalPreview.Kind.REMINDER, "약 먹기", "2026-10-16T09:00:00")), today, zone)
        assertEquals("알림 등록", reminder.label)
        assertEquals("오전 9:00", reminder.value)
        assertEquals(false, reminder.sub.orEmpty().contains("2026-10-16T"))
        assertEquals("등록하기", reminder.approveLabel)

        val memo = ApprovalSheetContent.of(request(ApprovalPreview(ApprovalPreview.Kind.MEMORY, "자물쇠 비밀번호는 수첩에")), today, zone)
        assertEquals("메모 저장", memo.label)
        assertNull(memo.value)
        assertEquals("저장하기", memo.approveLabel)
    }

    @Test
    fun `미리보기 없는 요청은 제목·설명, 프로필 제안은 남은 수와 무시·프로필에 저장`() {
        val plain = ApprovalSheetContent.of(request(null), today, zone)
        assertEquals("제목", plain.label)
        assertEquals("설명", plain.title)

        val s = ProfileSuggestion("p1", "직업", "개발자", null, ProfileSuggestionStatus.PENDING, 0, 0)
        val profile = ApprovalSheetContent.of(s, remaining = 2)
        assertEquals("프로필 저장 · 외 2건", profile.label)
        assertEquals("개발자", profile.title)
        assertEquals("무시", profile.rejectLabel)
        assertEquals("프로필에 저장", profile.approveLabel)
        assertEquals("프로필 저장", ApprovalSheetContent.of(s, remaining = 0).label)
    }
}
