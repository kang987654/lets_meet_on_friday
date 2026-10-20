package com.kosmos.app.feature.settings

import com.kosmos.app.R
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [AuditLabelsTest]
 * 활동 기록 표기(0.38.3 M3) — enum·툴 클래스 이름이 화면에 새지 않고, 기록 원문 형식(`Key: 값`)에서 내용 한 줄을 뽑는다.
 */
class AuditLabelsTest {

    private fun event(type: AuditEventType, details: String) = AuditEvent("e", type, "s1", details, 0L)

    @Test
    fun `툴 실행은 한국어 툴 이름, 결과 JSON 은 내용 줄에 두지 않는다`() {
        val display = AuditLabels.of(event(AuditEventType.TOOL_CALL, "Tool: AddReminder\nResult: {\"ok\":true}"))
        assertEquals("리마인더 추가 실행", display.title)
        assertEquals("", display.summary)
        assertEquals(R.drawable.ic_ph_bell, display.icon)
    }

    @Test
    fun `승인 기록 앞의 툴 이름도 한국어로 바꾼다`() {
        val display = AuditLabels.of(event(AuditEventType.APPROVAL_GRANTED, "AddSchedule: 팀 회의"))
        assertEquals("승인함", display.title)
        assertEquals("일정 추가: 팀 회의", display.summary)
    }

    @Test
    fun `모델 응답은 출력 줄만, 길면 자른다`() {
        val long = "가".repeat(120)
        val display = AuditLabels.of(event(AuditEventType.MODEL_RUN, "Prompt: 안녕\nOutput: $long\nTools declared: [AddMemory]"))
        assertEquals("모델 응답", display.title)
        assertEquals("가".repeat(80) + "…", display.summary)
    }

    @Test
    fun `모르는 툴과 형식 없는 내용은 원문 첫 줄`() {
        assertEquals("NewTool 실행", AuditLabels.of(event(AuditEventType.TOOL_CALL, "Tool: NewTool\nResult: x")).title)
        assertEquals("음성 전사 실패: 시간 초과", AuditLabels.of(event(AuditEventType.ERROR, "음성 전사 실패: 시간 초과\n둘째 줄")).summary)
        assertEquals("자전거 비밀번호", AuditLabels.of(event(AuditEventType.SEARCH_USED, "Query: 자전거 비밀번호")).summary)
    }
}
