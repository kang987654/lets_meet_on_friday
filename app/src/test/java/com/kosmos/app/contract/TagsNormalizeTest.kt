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
 *
 * [WHY] 표 형식 — 입력·기대값만 다른 사례 8개를 단건·목록 두 표로 모았다(0.27.x 정리, 사용자 확인).
 */
class TagsNormalizeTest {

    @Test
    fun `태그 하나 정규화 규칙`() {
        val cases = listOf(
            // [WHY] tags 칼럼이 콤마 조인 문자열이라 콤마가 든 태그는 읽을 때 두 개로 쪼개진다.
            "밥, 국" to "밥 국",            // 콤마 → 공백
            "a,,,b" to "a b",              // 연속 구분자 → 공백 하나
            "a   b" to "a b",              // 연속 공백 → 하나
            "  work  " to "work",          // 앞뒤 공백 제거
            ",work," to "work",            // 앞뒤 콤마 제거
            "work" to "work",              // 콤마 없음 — 그대로
            "긴 태그 이름" to "긴 태그 이름",
            ",,," to "",                   // 콤마만 — 빈 문자열
        )
        cases.forEach { (input, expected) ->
            assertEquals("입력 [$input]", expected, Tags.normalize(input))
        }
    }

    @Test
    fun `태그 목록 정규화 규칙`() {
        val cases = listOf(
            // [WHY] "a,b"와 "a b"는 서로 다른 입력이지만 정규화 후 둘 다 "a b"가 된다 — 새로 생긴 중복 제거.
            listOf("a,b", "a b") to listOf("a b"),
            listOf("work", "", "   ", ",") to listOf("work"),       // 빈 값·공백만인 값 제거
            listOf("work", "urgent") to listOf("work", "urgent"),   // 정상 목록 유지
        )
        cases.forEach { (input, expected) ->
            assertEquals("입력 $input", expected, Tags.normalizeAll(input))
        }
    }
}
