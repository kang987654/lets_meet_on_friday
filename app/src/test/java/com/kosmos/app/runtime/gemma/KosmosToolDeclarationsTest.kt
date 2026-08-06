package com.kosmos.app.runtime.gemma

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [KosmosToolDeclarationsTest]
 * 런타임 snake_case 이름을 우리 executor 계약으로 되돌리는 매핑을 검증합니다.
 *
 * [WHY] 0.8.1 실기기 preface 에서 런타임이 함수 이름뿐 아니라 **파라미터 이름도** snake_case
 * 로 선언하는 것이 확인됐다(start_time/end_time). 모델이 보내는 인자 키가 그 이름이므로,
 * 이 매핑이 깨지면 호출은 도착해도 인자가 전부 유실된다(requireString("startTime") 실패).
 */
class KosmosToolDeclarationsTest {

    @Test
    fun `snake_case 인자 키가 camelCase 로 되돌아온다`() {
        assertEquals("startTime", KosmosToolDeclarations.camelArgName("start_time"))
        assertEquals("endTime", KosmosToolDeclarations.camelArgName("end_time"))
    }

    @Test
    fun `단일 단어 키는 그대로 유지된다`() {
        assertEquals("title", KosmosToolDeclarations.camelArgName("title"))
        assertEquals("content", KosmosToolDeclarations.camelArgName("content"))
        assertEquals("tags", KosmosToolDeclarations.camelArgName("tags"))
        assertEquals("memo", KosmosToolDeclarations.camelArgName("memo"))
    }

    @Test
    fun `함수 이름은 executor 이름으로 되돌아온다`() {
        assertEquals("AddSchedule", KosmosToolDeclarations.canonicalName("add_schedule"))
        assertEquals("GetSchedule", KosmosToolDeclarations.canonicalName("get_schedule"))
        assertEquals("AddMemory", KosmosToolDeclarations.canonicalName("add_memory"))
        assertEquals("SearchWikipedia", KosmosToolDeclarations.canonicalName("search_wikipedia"))
    }

    @Test
    fun `알 수 없는 이름은 그대로 통과한다`() {
        assertEquals("unknown_tool", KosmosToolDeclarations.canonicalName("unknown_tool"))
        // 연속 밑줄은 빈 조각을 버리고 이어붙인다 — 예외를 던지지 않는 것이 계약이다.
        assertEquals("weirdKey", KosmosToolDeclarations.camelArgName("weird__key"))
    }
}
