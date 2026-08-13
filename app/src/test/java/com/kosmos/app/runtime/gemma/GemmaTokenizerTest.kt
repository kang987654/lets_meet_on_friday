package com.kosmos.app.runtime.gemma

import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GemmaTokenizerTest]
 * 토큰 추정기의 보수성 게이트를 exp26 실측 픽스처로 고정합니다.
 *
 * [WHY] 게이트는 두 방향이다 — **과소평가 0건**(슬라이딩 윈도우가 초과 히스토리를 실어 KV
 * 용량을 넘는 것을 막는다, ADR-020) AND **과대 1.5배 이내**(히스토리를 불필요하게 잃지
 * 않는다). 실측값은 `scratch/lab/exp26_token_ratio.py` 가 `engine.tokenize`(litert-lm 0.16.0,
 * gemma-4-E4B-it)로 잰 것이다. 추정식 계수를 바꾸면 이 픽스처로 같은 게이트를 다시 통과해야
 * 한다.
 *
 * [WHY] 예전 추정식 `length/3+1` 은 이 게이트의 과소평가 조건을 한국어에서 1.7~1.9배,
 * ISO 문자열에서 2.7배 어겼다 — 그 상태로는 이 테스트가 전부 실패한다.
 */
class GemmaTokenizerTest {

    private val tokenizer = GemmaTokenizer(mockk(), mockk())

    // exp26 실측: (텍스트, engine.tokenize 실토큰 수)
    private val measured = listOf(
        "내일 오후 3시에 치과 예약 잡아줘. 끝나고 장보러 갈 거야." to 20,
        "내 자전거 자물쇠 비밀번호가 뭐였지? 지난번에 알려줬잖아." to 19,
        "다음주 월요일 10시에 팀 회의 일정 추가해줘. 회의실은 3층이야." to 25,
        "오늘 하루 종일 비 온다는데 우산 챙기라고 아침에 알려줘." to 20,
        "어제 회식에서 먹은 삼겹살집 이름 기억해? 거기 또 가고 싶다." to 22,
        "The quick brown fox jumps over the lazy dog. Schedule a team meeting for next Monday at ten in the morning." to 23,
        "2026-08-17T10:00:00" to 19,
        "2026-08-17T10:00:00 2026-08-12T20:00:00 2026-11-03T09:30:00 1234 8282" to 69,
        """{"name":"add_schedule","arguments":{"title":"팀 회의","start_time":"2026-08-17T10:00:00","end_time":"2026-08-17T11:00:00","memo":"3층 회의실"}}""" to 71,
        "人工智能是研究、开发用于模拟、延伸和扩展人的智能的理论、方法、技术及应用系统的一门新的技术科学。" to 28,
        "다음 일정이 있습니다.\n- 2026-08-17T10:00:00 팀 회의 (3층 회의실)\n- 2026-08-18T15:00:00 치과 예약\n확인해 주세요." to 68
    )

    @Test
    fun `어떤 실측 샘플도 과소평가하지 않는다`() {
        measured.forEach { (text, actual) ->
            val estimate = tokenizer.sizeInTokens(text)
            assertTrue(
                "과소평가: est=$estimate < actual=$actual for \"${text.take(30)}…\"",
                estimate >= actual
            )
        }
    }

    @Test
    fun `과대평가는 실측의 1_5배 이내다`() {
        measured.forEach { (text, actual) ->
            val estimate = tokenizer.sizeInTokens(text)
            assertTrue(
                "과대평가: est=$estimate > 1.5 x actual=$actual for \"${text.take(30)}…\"",
                estimate <= Math.ceil(actual * 1.5).toInt()
            )
        }
    }
}
