package com.kosmos.app.contract

import com.kosmos.app.core.common.Tags
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [TagsNormalizeTest]
 * 태그 정규화 규칙을 검증합니다.
 *
 * [WHY] `:core`에 test source set이 없으므로 `SqlLikeEscapeTest`와 같은 방식으로
 * `:app` 테스트에서 검증한다(`:app`이 `:core`를 의존).
 */
class TagsNormalizeTest {

    @Test
    fun `콤마는 공백으로 치환된다`() {
        // [WHY] tags 칼럼이 콤마 조인 문자열이라 콤마가 든 태그는 읽을 때 두 개로 쪼개진다.
        assertEquals("밥 국", Tags.normalize("밥, 국"))
    }

    @Test
    fun `연속 공백은 하나로 접힌다`() {
        assertEquals("a b", Tags.normalize("a,,,b"))
        assertEquals("a b", Tags.normalize("a   b"))
    }

    @Test
    fun `앞뒤 공백은 제거된다`() {
        assertEquals("work", Tags.normalize("  work  "))
        assertEquals("work", Tags.normalize(",work,"))
    }

    @Test
    fun `콤마가 없으면 원문 그대로다`() {
        assertEquals("work", Tags.normalize("work"))
        assertEquals("긴 태그 이름", Tags.normalize("긴 태그 이름"))
    }

    @Test
    fun `콤마만인 태그는 빈 문자열이 된다`() {
        assertEquals("", Tags.normalize(",,,"))
    }

    @Test
    fun `정규화로 새로 생긴 중복은 제거된다`() {
        // [WHY] "a,b"와 "a b"는 서로 다른 입력이지만 정규화 후 둘 다 "a b"가 된다.
        assertEquals(listOf("a b"), Tags.normalizeAll(listOf("a,b", "a b")))
    }

    @Test
    fun `빈 값과 공백만인 값은 제거된다`() {
        assertEquals(listOf("work"), Tags.normalizeAll(listOf("work", "", "   ", ",")))
    }

    @Test
    fun `정상 태그 목록은 그대로 유지된다`() {
        assertEquals(
            listOf("work", "urgent"),
            Tags.normalizeAll(listOf("work", "urgent"))
        )
    }
}
