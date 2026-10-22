package com.kosmos.app.feature.settings

import com.kosmos.app.R
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [AuditLabelsTest]
 * 활동 기록 표기(0.38.3 M3) — enum·툴 클래스 이름이 화면에 새지 않고, 기록 원문 형식(`Key: 값`)에서 내용 한 줄을 뽑는다.
 * 펼침 항목·영어 오류·기억 추출·기억 합치기(0.38.4 M2).
 */
class AuditLabelsTest {

    private fun event(type: AuditEventType, details: String) = AuditEvent("e", type, "s1", details, 0L)

    @Test
    fun `툴 실행은 한국어 툴 이름, 결과 JSON 은 내용 줄에 두지 않는다`() {
        val display = AuditLabels.of(event(AuditEventType.TOOL_CALL, "Tool: AddReminder\nResult: {\"ok\":true}"))
        assertEquals("알림 추가 실행", display.title)
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

    @Test
    fun `펼침 — 채팅 답은 물은 말·답(마크다운 기호 뺌)·쓸 수 있던 도구`() {
        val display = AuditLabels.of(
            event(AuditEventType.MODEL_RUN, "Prompt: 출시 언제야?\nOutput: 출시는 **10-05**에 시작해요.\n둘째 줄\nTools declared: [AddSchedule, GetSchedule]")
        )
        assertEquals(listOf(AuditField("물은 말", "출시 언제야?"), AuditField("답", "출시는 10-05에 시작해요.\n둘째 줄")), display.fields)
        assertEquals(listOf("일정 추가", "일정 조회"), display.tags)
        assertEquals("출시는 10-05에 시작해요.", display.summary)
    }

    @Test
    fun `아침 브리핑·기억 추출은 각자의 이름으로`() {
        val briefing = AuditLabels.of(event(AuditEventType.MODEL_RUN, "Prompt: morning briefing (events=0, tasks=2, episodes=1)\nOutput: 좋은 아침이에요."))
        assertEquals("아침 브리핑 작성", briefing.title)
        val facts = AuditLabels.of(
            event(AuditEventType.MODEL_RUN, "Prompt: fact extraction (episode=e1, messages=4)\nOutput: profile=0 knowledge=1 savedKnowledge=1 filtered=0 suggested=2")
        )
        assertEquals("기억 추출", facts.title)
        assertEquals("메모 1개 저장, 프로필 제안 2개", facts.summary)
    }

    @Test
    fun `툴 결과는 상태와 메시지만, 기억 합치기는 한국어 이름`() {
        assertEquals(
            "성공, 기억에 저장했어요.",
            AuditLabels.of(event(AuditEventType.TOOL_CALL, "Tool: AddMemory\nResult: {\"status\":\"success\",\"message\":\"기억에 저장했어요.\"}")).summary
        )
        assertEquals(
            "실패, 시각 형식이 달라요",
            AuditLabels.of(event(AuditEventType.TOOL_CALL, "Tool: AddReminder\nResult: {\"status\":\"error\",\"message\":\"시각 형식이 달라요\"}")).summary
        )
        val merge = AuditLabels.of(event(AuditEventType.TOOL_CALL, "Tool: MemoryMerge\nResult: a + b → c"))
        assertEquals("기억 합치기 실행", merge.title)
        assertEquals("a + b → c", merge.summary)
    }

    @Test
    fun `영어 오류 원문은 한국어 사유로`() {
        assertEquals("답을 만들지 못했어요", AuditLabels.of(event(AuditEventType.ERROR, "Model inference failed: ModelInferenceError(reason=x)")).summary)
        assertEquals(
            "허용하지 않은 도구를 부르려 했어요: 위키백과 검색",
            AuditLabels.of(event(AuditEventType.ERROR, "Blocked tool call outside allowlist: SearchWikipedia")).summary
        )
        assertEquals(
            "도구에 넘긴 값이 잘못됐어요: 일정 추가",
            AuditLabels.of(event(AuditEventType.ERROR, "Invalid tool argument: AddSchedule.start_time (BAD_FORMAT)")).summary
        )
        assertEquals("앱 내부 오류", AuditLabels.of(event(AuditEventType.ERROR, "java.lang.IllegalStateException: boom")).summary)
    }
}
