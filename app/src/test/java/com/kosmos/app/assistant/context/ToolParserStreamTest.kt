package com.kosmos.app.assistant.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ToolParserStreamTest]
 * **점진적으로 도착하는** 응답에서 프로토콜 문법이 본문에 새지 않는지 검증합니다.
 *
 * [WHY] 기존 `ToolParserTest` 는 전부 **완결된 태그**만 먹인다. 그래서 스트리밍 중에만
 * 나타나는 결함 세 가지가 하나도 검증되지 않았다 — 열린 `<tool_call>` 노출, 열린
 * `<|think|>` 로 인한 빈 본문, 두 번째 생각 블록 누락.
 */
class ToolParserStreamTest {

    private fun contentOf(raw: String): String = ToolParser.parseStream(raw).content

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
    }

    @Test
    fun `parseStream strips a malformed tool call from content without counting it`() {
        // [WHY] 0.27.x — malformedToolCalls 필드는 소비자가 없어 삭제했다(사용자 확인). 남는 계약은
        // "깨진 블록이 본문에 새지 않는다" 이다.
        val raw = "확인해보겠습니다.<tool_call>{\"name\":\"AddSchedule\", \"args\":{</tool_call>"
        val result = ToolParser.parseStream(raw)

        assertEquals("확인해보겠습니다.", result.content)
    }

    @Test
    fun `parseStream extracts both thinking and tool call`() {
        val raw = "<|think|>어떻게 할까?</|think|>알겠습니다.<tool_call>{\"name\":\"GetSchedule\", \"args\":{\"date\":\"today\"}}</tool_call>"
        val result = ToolParser.parseStream(raw)
        
        assertEquals("어떻게 할까?", result.thinking)
        assertEquals("알겠습니다.", result.content)
    }

    @Test
    fun `열린 tool_call 은 본문에 새지 않는다`() {
        val raw = "확인해보겠습니다.<tool_call>{\"name\":\"AddSch"

        assertEquals("확인해보겠습니다.", contentOf(raw))
    }

    @Test
    fun `토큰 경계에 걸린 태그 조각도 노출되지 않는다`() {
        // [WHY] 태그가 완성되기 전 접두사도 잘라야 한다. 자르지 않으면 `<tool_c` 같은
        // 조각이 한 글자씩 자라는 것이 그대로 보인다.
        val prefixes = listOf("<", "<t", "<to", "<too", "<tool", "<tool_", "<tool_c", "<tool_ca", "<tool_cal", "<tool_call")
        prefixes.forEach { prefix ->
            assertEquals("접두사 '$prefix' 가 노출됐다", "네, ", contentOf("네, $prefix"))
        }
    }

    @Test
    fun `점진 공급 중 어떤 시점에도 프로토콜 문법이 보이지 않는다`() {
        val full = "잠시만요.<tool_call>{\"name\":\"GetSchedule\", \"args\":{\"date\":\"today\"}}</tool_call>"

        for (length in 1..full.length) {
            val content = contentOf(full.substring(0, length))
            assertFalse("길이 $length 에서 노출: $content", content.contains("<tool"))
            assertFalse("길이 $length 에서 노출: $content", content.contains("\"name\""))
        }
    }

    @Test
    fun `점진 공급 중 본문이 줄어들지 않는다`() {
        val full = "오늘 일정은 두 건입니다. 회의와 점심이에요."

        var previous = ""
        for (length in 1..full.length) {
            val content = contentOf(full.substring(0, length))
            assertTrue("길이 $length 에서 본문이 역행했다: '$previous' → '$content'", content.length >= previous.length)
            previous = content
        }
        assertEquals(full, previous)
    }

    @Test
    fun `열린 생각 블록 구간에서는 본문이 비고 사고 과정만 자란다`() {
        val raw = "<|think|>어떻게 답할지 고민 중"
        val parsed = ToolParser.parseStream(raw)

        // [WHY] 본문이 빈 문자열이 되는 것이 정상이다. 호출부(BaseAgent)가 이것을 null 로
        // 정규화해야 UI 가 타이핑 인디케이터를 유지한다.
        assertEquals("", parsed.content)
        assertEquals("어떻게 답할지 고민 중", parsed.thinking)
    }

    @Test
    fun `생각 블록이 두 개면 모두 제거되고 합쳐진다`() {
        // [WHY] 예전에는 find(단수)여서 두 번째 블록이 본문에 남아 노출됐다.
        val raw = "<|think|>첫째</|think|>중간 문장.<|think|>둘째</|think|>끝 문장."
        val parsed = ToolParser.parseStream(raw)

        assertFalse("두 번째 생각 블록이 본문에 남았다: ${parsed.content}", parsed.content.contains("think"))
        assertFalse(parsed.content.contains("둘째"))
        assertEquals("첫째\n둘째", parsed.thinking)
    }

    @Test
    fun `완결된 tool_call 과 뒤따르는 열린 tool_call 이 모두 본문에서 걷힌다`() {
        val raw = "처리합니다.<tool_call>{\"name\":\"GetSchedule\", \"args\":{}}</tool_call>추가로<tool_call>{\"na"
        val parsed = ToolParser.parseStream(raw)

        assertEquals("처리합니다.추가로", parsed.content)
    }
}
