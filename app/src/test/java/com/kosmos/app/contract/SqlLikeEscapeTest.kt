package com.kosmos.app.contract

import com.kosmos.app.core.common.SqlLike
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SqlLikeEscapeTest]
 * `LIKE` 패턴 이스케이프 규칙을 검증합니다.
 *
 * [WHY] `:core`에 test source set이 없으므로 `ErrorMessageMappingTest`와 같은 방식으로
 * `:app` 테스트에서 검증한다(`:app`이 `:core`를 의존).
 *
 * [WHY] 표 형식 — 사례 6개가 입력·기대값만 다른 한 줄짜리였다(0.27.x 정리, 사용자 확인).
 */
class SqlLikeEscapeTest {

    @Test
    fun `LIKE 특수문자 이스케이프 규칙`() {
        val cases = listOf(
            "100%" to "100\\%",               // 퍼센트
            "snake_case" to "snake\\_case",   // 언더스코어
            "a\\b" to "a\\\\b",               // 백슬래시
            // [WHY] 백슬래시가 먼저 처리돼야 한다 — 순서가 틀리면 %를 위해 넣은 백슬래시를 다시
            // 이스케이프해 패턴이 망가진다. 입력 `\%` → `\\` + `\%` = `\\\%`
            "\\%" to "\\\\\\%",
            "회의 준비" to "회의 준비",          // 특수문자 없음
            "" to "",                          // 빈 문자열
        )
        cases.forEach { (input, expected) ->
            assertEquals("입력 [$input]", expected, SqlLike.escape(input))
        }
    }
}
