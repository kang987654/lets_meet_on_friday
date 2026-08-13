package com.kosmos.app.domain.util

import com.kosmos.app.domain.tool.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SentenceTruncator] 의 절단 계약을 고정합니다.
 *
 * [WHY] 토크나이저를 1자=1토큰 스텁으로 두면 경계 산술을 정확한 수로 단언할 수 있다 —
 * 실제 추정기의 계수가 바뀌어도 이 테스트의 검증 대상(경계 선택 논리)은 흔들리지 않는다.
 */
class SentenceTruncatorTest {

    private val charTokenizer = object : Tokenizer {
        override fun sizeInTokens(text: String): Int = text.length
    }

    @Test
    fun `예산 이내면 원문 그대로다`() {
        val text = "가나다. 라마바."
        assertEquals(text, SentenceTruncator.truncate(text, maxTokens = 100, tokenizer = charTokenizer))
    }

    @Test
    fun `예산을 넘으면 문장 경계에서 자른다`() {
        val text = "가나다. 라마바. 사아자."
        // 전체 14토큰 > 10 → 마지막 경계(14)는 초과, 그 앞 경계(9)가 예산 이내.
        assertEquals(
            "가나다. 라마바.",
            SentenceTruncator.truncate(text, maxTokens = 10, tokenizer = charTokenizer)
        )
    }

    @Test
    fun `표식 비용도 예산에서 차감된다`() {
        val text = "가나다. 라마바. 사아자."
        val result = SentenceTruncator.truncate(text, maxTokens = 12, tokenizer = charTokenizer, marker = "XX")
        // 예산 12 - 표식 2 = 10 → "가나다. 라마바."(9) + "XX"
        assertEquals("가나다. 라마바.XX", result)
        assertTrue(charTokenizer.sizeInTokens(result) <= 12)
    }

    @Test
    fun `줄바꿈도 문장 경계다`() {
        val text = "첫째 줄\n둘째 줄\n셋째 줄"
        // "첫째 줄\n둘째 줄\n" = 10토큰이 예산에 정확히 들어온다.
        assertEquals(
            "첫째 줄\n둘째 줄\n",
            SentenceTruncator.truncate(text, maxTokens = 10, tokenizer = charTokenizer)
        )
    }

    @Test
    fun `한 문장이 예산을 넘으면 하드컷하되 빈 결과는 내지 않는다`() {
        val text = "가나다라마바사아자차"
        val result = SentenceTruncator.truncate(text, maxTokens = 5, tokenizer = charTokenizer)
        assertEquals("가나다라마", result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `예산이 표식보다 작아도 최소 한 글자는 남는다`() {
        val result = SentenceTruncator.truncate(
            "가나다라마바",
            maxTokens = 2,
            tokenizer = charTokenizer,
            marker = "긴표식문자열"
        )
        assertTrue(result.startsWith("가"))
    }
}
