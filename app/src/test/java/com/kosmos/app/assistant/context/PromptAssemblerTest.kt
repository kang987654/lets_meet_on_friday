package com.kosmos.app.assistant.context

import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.modelrunner.ChatPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PromptAssemblerTest {

    private lateinit var promptAssembler: PromptAssembler

    @Before
    fun setup() {
        promptAssembler = PromptAssembler()
    }

    private fun context(
        responseStyle: String = "DEFAULT",
        conversations: List<ChatMessage> = emptyList(),
        maxTokens: Int = Constants.MAX_CONTEXT_TOKENS
    ) = ContextBuilder.Context(
        recentConversations = conversations,
        sessionId = "s1",
        responseStyle = responseStyle,
        maxTokens = maxTokens
    )

    private fun prompt(
        responseStyle: String = "DEFAULT",
        conversations: List<ChatMessage> = emptyList(),
        maxTokens: Int = Constants.MAX_CONTEXT_TOKENS,
        userInput: String = "내일 3시에 치과 예약 잡아줘"
    ): ChatPrompt = promptAssembler.assembleWithTools(
        context = context(responseStyle, conversations, maxTokens),
        userInput = userInput,
        availableTools = listOf("AddSchedule", "GetSchedule", "AddMemory"),
        systemRole = "personal assistant named Kosmos"
    )

    private fun promptWith(availableTools: List<String>): ChatPrompt =
        promptAssembler.assembleWithTools(
            context = context(),
            userInput = "내 자전거 비밀번호 뭐였지?",
            availableTools = availableTools,
            systemRole = "personal assistant named Kosmos"
        )

    private fun message(content: String, role: ChatMessage.Role = ChatMessage.Role.USER) = ChatMessage(
        id = "m1",
        sessionId = "s1",
        role = role,
        content = content,
        inputType = InputType.TEXT,
        searchUsed = false,
        createdAt = 0L
    )

    // --- 시스템 지시는 하루 동안 고정된다 (프리필 재사용의 전제) ---

    @Test
    fun `시스템 지시에는 턴마다 달라지는 값이 들어가지 않는다`() {
        // [WHY] 이 테스트가 프리필 성능의 회귀 방지다. 분 단위 시각이나 검색된 기억이 시스템
        // 지시로 새어 들어가면 런타임의 재사용 판정이 매 턴 거짓이 되고, 툴 선언(~2천 토큰)까지
        // 전부 다시 프리필된다 — PC 실측으로 2번째 턴이 0.5초에서 3.8초로 늘었다.
        val instruction = prompt(conversations = listOf(message("자물쇠 비밀번호는 8282"))).systemInstruction

        assertFalse("분 단위 시계가 시스템 지시에 있다", instruction.contains("Current Time"))
        assertFalse("대화 내용이 시스템 지시로 새면 안 된다", instruction.contains("8282"))
    }

    @Test
    fun `같은 날 같은 설정이면 시스템 지시가 두 번 조립해도 동일하다`() {
        // [WHY] 문자열 동일성이 런타임의 대화 재사용 조건 그 자체다
        // (GemmaModelRunner.getOrCreateConversation 의 isSameSystemInstruction). 대화 내용이
        // 달라져도 시스템 지시는 같아야 한다.
        val first = prompt(conversations = listOf(message("첫 기억"))).systemInstruction
        val second = prompt(conversations = listOf(message("다른 기억"))).systemInstruction

        assertEquals(first, second)
    }

    @Test
    fun `기억 조회 툴이 있으면 저장과 방향을 구분하는 규칙이 붙는다`() {
        // [WHY] 회상("뭐였지?")과 저장("기억해줘")은 트리거 표현이 비슷해서 모델이
        // `add_memory` 를 잘못 부르는 것이 실측됐다. 규칙에서 방향을 못 박아야 한다.
        val instruction = promptWith(listOf("AddMemory", "SearchMemory")).systemInstruction

        assertTrue(instruction.contains("`search_memory`"))
        assertTrue(instruction.contains("never call `add_memory` for it"))
    }

    @Test
    fun `선언되지 않은 툴의 규칙은 나오지 않는다`() {
        val instruction = promptWith(listOf("AddMemory")).systemInstruction

        assertTrue(instruction.contains("`add_memory`"))
        assertFalse(instruction.contains("`search_memory`"))
    }

    @Test
    fun `설정의 프리필 예산이 프롬프트로 전달된다`() {
        // [WHY] 런타임이 이 값으로 대화 재설정 임계값을 정한다. 전달되지 않으면 설정을
        // 내려도 살아 있는 대화의 KV 가 계속 자란다.
        assertEquals(3000, prompt(maxTokens = 3000).contextBudgetTokens)
    }

    // --- 상대 날짜 해석 (PC 실험 결과 고정) ---

    @Test
    fun `시스템 지시가 오늘 기준 파생 날짜를 계산해 제공한다`() {
        // [WHY] PC 실험에서 시각만 주면 모델이 "다음주 월요일"을 한 주 틀리게(8/10 → 8/17)
        // 계산했다. 파생 날짜를 우리가 계산해 주는 것이 일정 등록의 전제다.
        val now = java.time.LocalDate.now()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val nextMonday = now.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
        val instruction = prompt().systemInstruction

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
        val instruction = prompt().systemInstruction

        assertTrue(instruction.contains("Resolve relative dates and times yourself"))
        assertTrue(instruction.contains("are NOT missing information"))
        assertTrue(instruction.contains("Optional parameters are never a reason to ask"))
        assertFalse(
            "예전 문구가 되살아나면 모델이 상대 날짜를 되묻는다",
            instruction.contains("If you lack mandatory information")
        )
    }

    @Test
    fun `날짜 규칙이 가리키는 위치에 실제로 날짜 블록이 있다`() {
        // [WHY] 규칙은 "the [System Data] values above" 라고 같은 시스템 지시 안을 가리킨다.
        // 블록이 이 문구보다 뒤에 오거나 아예 다른 메시지로 옮겨지면 규칙이 무력해진다.
        val instruction = prompt().systemInstruction
        val blockAt = instruction.indexOf("[System Data] Today:")
        val ruleAt = instruction.indexOf("[System Data] values above")

        assertTrue("날짜 블록이 없다", blockAt >= 0)
        assertTrue("규칙 문구가 없다", ruleAt >= 0)
        assertTrue("블록이 규칙보다 앞(above)에 있어야 한다", blockAt < ruleAt)
    }

    // --- 응답 스타일 ---

    @Test
    fun `기본 스타일에서는 스타일 지시가 없다`() {
        val instruction = prompt(responseStyle = "DEFAULT").systemInstruction

        assertTrue(instruction.contains("[System]"))
        assertTrue(instruction.contains("You are a personal assistant named Kosmos"))
        assertFalse(instruction.contains("[Style:"))
    }

    @Test
    fun `설정 화면의 스타일 값은 실제 지시문으로 풀린다`() {
        // [WHY] 예전에는 `[Style: CONCISE]` 라벨만 넣어, 모델이 그 대문자 토큰이 무엇을
        // 요구하는지 알 길이 없었다 — 설정이 사실상 아무 효과가 없었다.
        val concise = prompt(responseStyle = "CONCISE").systemInstruction
        val detailed = prompt(responseStyle = "DETAILED").systemInstruction

        assertTrue(concise.contains("one or two short sentences"))
        assertFalse("라벨을 그대로 흘리면 안 된다", concise.contains("[Style: CONCISE]"))
        assertTrue(detailed.contains("Answer thoroughly"))
        assertFalse(detailed.contains("[Style: DETAILED]"))
    }

    @Test
    fun `설정 화면에 없는 자유 문장 스타일은 그대로 전달된다`() {
        val testStyle = "친절하게 이모지 많이 써줘"
        val instruction = prompt(responseStyle = testStyle).systemInstruction

        assertTrue(instruction.contains("[Style: $testStyle]"))
    }
}
