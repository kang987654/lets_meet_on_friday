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
 */
class SqlLikeEscapeTest {

    @Test
    fun `퍼센트는 이스케이프된다`() {
        assertEquals("100\\%", SqlLike.escape("100%"))
    }

    @Test
    fun `언더스코어는 이스케이프된다`() {
        assertEquals("snake\\_case", SqlLike.escape("snake_case"))
    }

    @Test
    fun `백슬래시는 이스케이프된다`() {
        assertEquals("a\\\\b", SqlLike.escape("a\\b"))
    }

    @Test
    fun `백슬래시가 먼저 처리되어 이중 이스케이프가 생기지 않는다`() {
        // [WHY] 순서가 틀리면 %를 위해 넣은 백슬래시를 다시 이스케이프해 패턴이 망가진다.
        // 입력 `\%` → 백슬래시 1개(`\\`) + 퍼센트 1개(`\%`) = `\\\%`
        assertEquals("\\\\\\%", SqlLike.escape("\\%"))
    }

    @Test
    fun `특수문자가 없으면 원문 그대로다`() {
        assertEquals("회의 준비", SqlLike.escape("회의 준비"))
    }

    @Test
    fun `빈 문자열은 빈 문자열이다`() {
        assertEquals("", SqlLike.escape(""))
    }
}
