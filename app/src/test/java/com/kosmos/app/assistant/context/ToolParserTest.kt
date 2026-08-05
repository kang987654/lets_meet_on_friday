package com.kosmos.app.assistant.context

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToolParserTest {

    @Test
    fun `parseStream extracts thinking process correctly`() {
        val raw = "<|think|>이 질문은 일정을 추가해 달라는 요청이다.</|think|>네, 일정을 추가해 드릴게요."
        val result = ToolParser.parseStream(raw)
        
        assertEquals("이 질문은 일정을 추가해 달라는 요청이다.", result.thinking)
        assertEquals("네, 일정을 추가해 드릴게요.", result.content)
    }

    @Test
    fun `parseStream extracts tool call correctly`() {
        val raw = "잠시만요, 일정을 추가하겠습니다.<tool_call>{\"name\":\"AddSchedule\", \"args\":{\"title\":\"미팅\", \"startTime\":\"2026-07-14T15:00:00Z\", \"endTime\":\"2026-07-14T16:00:00Z\", \"description\":\"\"}}</tool_call>"
        val result = ToolParser.parseStream(raw)
        
        assertEquals("잠시만요, 일정을 추가하겠습니다.", result.content)
        assertEquals(1, result.toolCalls.size)
        
        val toolCall = result.toolCalls[0]
        assertEquals("AddSchedule", toolCall.name)
        assertEquals("미팅", toolCall.args.optString("title"))
    }

    @Test
    fun `parseStream surfaces malformed tool call instead of silently dropping it`() {
        // [WHY] 이전에는 빈 catch 로 삼켜서 툴 콜이 흔적 없이 사라졌고, 모델은 오류를 받지
        // 못한 채 그 턴이 평문 답변으로 처리됐다.
        val raw = "확인해보겠습니다.<tool_call>{\"name\":\"AddSchedule\", \"args\":{</tool_call>"
        val result = ToolParser.parseStream(raw)

        assertEquals(0, result.toolCalls.size)
        assertEquals(1, result.malformedToolCalls.size)
        assertEquals("확인해보겠습니다.", result.content)
    }

    @Test
    fun `parseStream treats a tool call without a name as malformed`() {
        val raw = "<tool_call>{\"args\":{\"title\":\"미팅\"}}</tool_call>"
        val result = ToolParser.parseStream(raw)

        assertEquals(0, result.toolCalls.size)
        assertEquals(1, result.malformedToolCalls.size)
    }

    @Test
    fun `parseStream yields empty args when the tool call omits them`() {
        val raw = "<tool_call>{\"name\":\"GetSchedule\"}</tool_call>"
        val result = ToolParser.parseStream(raw)

        assertEquals(1, result.toolCalls.size)
        assertEquals(null, result.toolCalls[0].args.optString("date"))
    }

    @Test
    fun `parseStream extracts both thinking and tool call`() {
        val raw = "<|think|>어떻게 할까?</|think|>알겠습니다.<tool_call>{\"name\":\"GetSchedule\", \"args\":{\"date\":\"today\"}}</tool_call>"
        val result = ToolParser.parseStream(raw)
        
        assertEquals("어떻게 할까?", result.thinking)
        assertEquals("알겠습니다.", result.content)
        assertEquals(1, result.toolCalls.size)
        assertEquals("GetSchedule", result.toolCalls[0].name)
    }
}
