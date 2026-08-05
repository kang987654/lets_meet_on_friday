package com.kosmos.app.assistant.tool

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [ToolArgumentsTest]
 * 툴 인자 해석 규칙을 검증합니다.
 *
 * [WHY] 첫 번째 테스트가 실제 버그의 회귀 방지다 — org.json 은 JSON 배열을 List 가 아닌
 * JSONArray 로 주기 때문에, 기존 `tagsRaw is List<*>` 분기가 절대 참이 되지 않아
 * 프롬프트대로 보낸 태그가 전부 유실됐다.
 */
@RunWith(RobolectricTestRunner::class)
class ToolArgumentsTest {

    private fun args(json: String) = ToolArguments(JSONObject(json))

    // --- stringList ---

    @Test
    fun `JSON 배열 태그가 유실되지 않는다`() {
        val result = args("""{"tags":["work","urgent"]}""").stringList("tags")
        assertEquals(listOf("work", "urgent"), result)
    }

    @Test
    fun `콤마 문자열 태그는 트림되어 분리된다`() {
        val result = args("""{"tags":"work, urgent "}""").stringList("tags")
        assertEquals(listOf("work", "urgent"), result)
    }

    @Test
    fun `단일 문자열 태그도 받는다`() {
        assertEquals(listOf("work"), args("""{"tags":"work"}""").stringList("tags"))
    }

    @Test
    fun `태그 키가 없으면 빈 목록이다`() {
        assertEquals(emptyList<String>(), args("""{}""").stringList("tags"))
    }

    @Test
    fun `배열 안의 빈 값과 null은 제거된다`() {
        val result = args("""{"tags":["work","","  ",null,"urgent"]}""").stringList("tags")
        assertEquals(listOf("work", "urgent"), result)
    }

    // --- optString / requireString ---

    @Test
    fun `문자열 인자를 그대로 읽는다`() {
        assertEquals("미팅", args("""{"title":"미팅"}""").optString("title"))
    }

    @Test
    fun `숫자는 문자열로 강제 변환된다`() {
        // [WHY] 모델이 따옴표를 빠뜨리는 것은 흔한 실수다. 여기서 거부하면 사용자가 정상
        // 요청을 했음에도 모델이 같은 인자를 반복 요구하는 루프에 빠진다.
        assertEquals("20260806", args("""{"startTime":20260806}""").requireString("startTime"))
    }

    @Test
    fun `불린도 문자열로 강제 변환된다`() {
        assertEquals("true", args("""{"flag":true}""").optString("flag"))
    }

    @Test
    fun `객체가 문자열 자리에 오면 타입 오류다`() {
        // [WHY] 배열·객체는 의미가 다른 값이므로 강제 변환하지 않고 타입 오류로 보고한다.
        val e = runCatching { args("""{"title":{"a":1}}""").optString("title") }
            .exceptionOrNull() as ToolArgumentException
        assertEquals("title", e.field)
        assertEquals(ToolArgumentException.Reason.WRONG_TYPE, e.reason)
    }

    @Test
    fun `키가 없으면 누락으로 보고한다`() {
        val e = runCatching { args("""{}""").requireString("title") }
            .exceptionOrNull() as ToolArgumentException
        // 누락과 타입 오류가 구분되어야 모델이 무엇을 고칠지 알 수 있다.
        assertEquals(ToolArgumentException.Reason.MISSING, e.reason)
    }

    @Test
    fun `공백만인 값은 누락으로 취급한다`() {
        val e = runCatching { args("""{"title":"   "}""").requireString("title") }
            .exceptionOrNull() as ToolArgumentException
        assertEquals(ToolArgumentException.Reason.MISSING, e.reason)
    }

    // --- JSON null 정규화 ---

    @Test
    fun `JSON null은 값 없음으로 정규화된다`() {
        // [WHY] JSONObject.NULL 은 non-null Any 라서 has()/!= null 검사가 틀린다.
        val a = args("""{"description":null}""")
        assertNull(a.optString("description"))
        assertFalse(a.has("description"))
        assertEquals(emptyList<String>(), a.stringList("description"))
    }

    @Test
    fun `존재하는 값에는 has가 참이다`() {
        assertTrue(args("""{"title":"미팅"}""").has("title"))
    }

    @Test
    fun `args가 없는 툴 콜은 빈 인자로 다뤄진다`() {
        val empty = ToolArguments.empty()
        assertFalse(empty.has("anything"))
        assertNull(empty.optString("anything"))
        assertEquals(emptyList<String>(), empty.stringList("anything"))
    }
}
