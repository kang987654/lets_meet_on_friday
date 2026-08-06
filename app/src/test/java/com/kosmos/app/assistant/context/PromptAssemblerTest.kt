package com.kosmos.app.assistant.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PromptAssemblerTest {

    private lateinit var promptAssembler: PromptAssembler

    @Before
    fun setup() {
        promptAssembler = PromptAssembler()
    }

    @Test
    fun `assemble system block correctly without custom response style when DEFAULT`() {
        val testContext = ContextBuilder.Context(
            recentConversations = emptyList(),
            sessionId = "session-1",
            responseStyle = "DEFAULT"
        )
        
        val chatPrompt = promptAssembler.assemble(testContext, "test input")
        
        assertTrue(chatPrompt.systemInstruction.contains("[System]"))
        assertTrue(chatPrompt.systemInstruction.contains("You are a personal assistant named Kosmos."))
        assertFalse(chatPrompt.systemInstruction.contains("[Style:"))
    }

    // --- 상대 날짜 해석 (PC 실험 결과 고정) ---

    private fun toolPrompt() = promptAssembler.assembleWithTools(
        context = ContextBuilder.Context(
            recentConversations = emptyList(),
            sessionId = "s1",
            responseStyle = "DEFAULT"
        ),
        userInput = "내일 3시에 치과 예약 잡아줘",
        availableTools = listOf("AddSchedule", "GetSchedule", "AddMemory"),
        systemRole = "personal assistant named Kosmos"
    ).systemInstruction

    @Test
    fun `시스템 지시가 오늘 기준 파생 날짜를 계산해 제공한다`() {
        // [WHY] PC 실험에서 시각만 주면 모델이 "다음주 월요일"을 한 주 틀리게(8/10 → 8/17)
        // 계산했다. 파생 날짜를 우리가 계산해 주는 것이 일정 등록의 전제다.
        val now = java.time.LocalDate.now()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val nextMonday = now.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
        val instruction = toolPrompt()

        assertTrue(instruction.contains("오늘=${now.format(fmt)}"))
        assertTrue(instruction.contains("내일=${now.plusDays(1).format(fmt)}"))
        assertTrue(instruction.contains("모레=${now.plusDays(2).format(fmt)}"))
        assertTrue(instruction.contains("다음주 월요일=${nextMonday.format(fmt)}"))
    }

    @Test
    fun `다음주 월요일은 항상 오늘 이후의 월요일이다`() {
        // [WHY] 한국어에서 주는 월요일에 시작하므로 오늘이 월요일이면 7일 뒤여야 한다.
        val now = java.time.LocalDate.now()
        val nextMonday = now.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))

        assertEquals(java.time.DayOfWeek.MONDAY, nextMonday.dayOfWeek)
        assertTrue("오늘 이후여야 한다", nextMonday.isAfter(now))
        assertTrue("한 주 이내여야 한다", nextMonday.isBefore(now.plusDays(8)))
    }

    @Test
    fun `상대 날짜를 되묻지 말라는 지시가 있고 예전의 추측 금지 문구는 없다`() {
        // [WHY] "If you lack mandatory information … DO NOT guess. Ask the user first." 가
        // 일정 등록을 막는 주범이었다(PC 실험: 4케이스 중 3 실패 → 교체 후 4/4). 이 회귀가
        // 되돌아오면 일정 기능이 조용히 죽으므로 문구를 테스트로 고정한다.
        val instruction = toolPrompt()

        assertTrue(instruction.contains("Resolve relative dates and times yourself"))
        assertTrue(instruction.contains("are NOT missing information"))
        assertTrue(instruction.contains("Optional parameters are never a reason to ask"))
        assertFalse(
            "예전 문구가 되살아나면 모델이 상대 날짜를 되묻는다",
            instruction.contains("If you lack mandatory information")
        )
    }

    @Test
    fun `assemble system block correctly with custom response style when not DEFAULT`() {
        val testStyle = "친절하게 이모지 많이 써줘"
        val testContext = ContextBuilder.Context(
            recentConversations = emptyList(),
            sessionId = "session-1",
            responseStyle = testStyle
        )
        
        val chatPrompt = promptAssembler.assemble(testContext, "test input")
        
        assertTrue(chatPrompt.systemInstruction.contains("[System]"))
        assertTrue(chatPrompt.systemInstruction.contains("You are a personal assistant named Kosmos."))
        assertTrue(chatPrompt.systemInstruction.contains("[Style: $testStyle]"))
    }
}
