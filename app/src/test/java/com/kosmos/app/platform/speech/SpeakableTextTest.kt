package com.kosmos.app.platform.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SpeakableTextTest]
 * 낭독용 텍스트 정리(마크다운·URL·이모지 제거)와 엔진 상한 분할을 표로 고정합니다 (0.29.0).
 */
class SpeakableTextTest {

    @Test
    fun `마크다운 기호를 걷어내고 글자는 남긴다`() {
        val cases = listOf(
            "**중요**: 3시 회의" to "중요: 3시 회의",
            "# 오늘 일정\n- 치과 예약\n- 운동" to "오늘 일정\n치과 예약\n운동",
            "1. 첫째\n2) 둘째" to "첫째\n둘째",
            "[위키백과](https://ko.wikipedia.org/wiki/x) 참고" to "위키백과 참고",
            "주소는 https://example.com 입니다" to "주소는 입니다",
            "코드:\n```kotlin\nval x = 1\n```\n끝" to "코드:\n끝",
            "`adb` 명령" to "adb 명령",
            "> 인용문" to "인용문",
            "좋아요 😀👍" to "좋아요",
            "~~취소~~ 확정" to "취소 확정",
            "```\n코드만\n```" to "",
        )
        cases.forEach { (input, expected) -> assertEquals(input, expected, SpeakableText.from(input)) }
    }

    @Test
    fun `문장 경계에서 상한 이하로 나누고 긴 문장은 글자 수로 자른다`() {
        assertEquals(listOf("가나. 다라."), SpeakableText.chunks("가나. 다라.", 20))
        assertEquals(listOf("가나다.", "라마바."), SpeakableText.chunks("가나다. 라마바.", 6))
        val long = "가".repeat(25)
        val chunks = SpeakableText.chunks(long, 10)
        assertEquals(listOf(10, 10, 5), chunks.map { it.length })
        assertTrue(SpeakableText.chunks("", 10).isEmpty())
    }
}
