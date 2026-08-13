package com.kosmos.app.core

import com.kosmos.app.core.common.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TokenBudgetInvariantTest]
 * 토큰 예산이 **엔진이 담을 수 있는 양을 넘지 않는지** 못박습니다.
 *
 * [WHY] 2026-08-12 실기기에서 앱은 프리필 예산을 6000(슬라이더 최대 8000)으로 잡으면서
 * `EngineConfig.maxNumTokens` 를 넘기지 않았다. 넘기지 않으면 상한이 없어지는 것이 아니라
 * 런타임 기본값 4096 이 되므로, **담을 수 있는 양보다 큰 프롬프트를 의도적으로 만들고 있었다.**
 * 파이썬 0.15.0 은 오류를 내지만(`5857 >= 4096`) AAR 0.14.0 은 오류 없이 답을 냈다 — 초과분을
 * 조용히 처리한다. 그래서 이 어긋남은 크래시가 아니라 **품질 저하**로만 나타나고, 그 형태로는
 * 원인을 찾기가 매우 어렵다.
 *
 * [WHY] 이 값들은 서로 다른 파일에 흩어져 있고(상수·설정 화면·DataStore·런타임) 각자 그럴듯한
 * 이유로 바뀐다. 관계를 코드로 강제할 방법이 없으므로 테스트로 고정한다. 하나라도 어긋나면
 * 여기서 빨간불이 켜진다.
 */
class TokenBudgetInvariantTest {

    @Test
    fun `프리필 천장은 엔진 용량에서 생성 여유를 뺀 값이다`() {
        // [WHY] 프롬프트와 생성 토큰이 같은 KV 에 들어간다. 프리필만 용량에 맞추면 답변이
        // 길어질 때 경계를 넘는다.
        assertEquals(
            Constants.ENGINE_MAX_TOKENS - Constants.GENERATION_HEADROOM_TOKENS,
            Constants.PREFILL_CEILING_TOKENS
        )
        assertTrue("생성 여유가 0이면 천장의 의미가 없다", Constants.GENERATION_HEADROOM_TOKENS > 0)
    }

    @Test
    fun `기본 프리필 예산이 천장을 넘지 않는다`() {
        assertTrue(
            "기본 예산 ${Constants.MAX_CONTEXT_TOKENS} 이 천장 ${Constants.PREFILL_CEILING_TOKENS} 을 넘는다",
            Constants.MAX_CONTEXT_TOKENS <= Constants.PREFILL_CEILING_TOKENS
        )
    }

    @Test
    fun `대화 재설정 임계값이 천장을 넘지 않는다`() {
        // [WHY] 런타임은 `budget.coerceAtLeast(MIN_CONVERSATION_RESET_TOKENS)` 로 임계값을 만든다.
        // 이 하한이 천장보다 크면, 예산을 낮춰도 임계값이 밀려 올라가 **대화가 용량을 넘도록
        // 자라는 것을 허용**한다 — 재설정을 막으려는 하한이 오히려 초과를 보장한다.
        assertTrue(
            "리셋 하한 ${Constants.MIN_CONVERSATION_RESET_TOKENS} 이 천장 ${Constants.PREFILL_CEILING_TOKENS} 을 넘는다",
            Constants.MIN_CONVERSATION_RESET_TOKENS <= Constants.PREFILL_CEILING_TOKENS
        )
    }

    @Test
    fun `오버헤드 예약 후에도 히스토리에 쓸 예산이 남는다`() {
        // [WHY] 오버헤드는 실측 1,329(시스템 지시 573 + 툴 선언 652 + few-shot 104)에 여유를
        // 얹은 값이다. 예약이 예산을 다 먹으면 히스토리가 0이 되어 대화가 성립하지 않는다.
        val history = Constants.MAX_CONTEXT_TOKENS - Constants.PREFILL_OVERHEAD_TOKENS
        assertTrue(
            "히스토리 예산이 $history 밖에 안 남는다",
            history >= Constants.MIN_HISTORY_TOKENS
        )
    }

    @Test
    fun `오버헤드 예약이 실측값보다 크고 과하지 않다`() {
        // [WHY] 2600 이었고 주석은 "툴 선언만 ~2천 토큰" 이라고 적었는데 둘 다 틀렸다. 실측
        // 합계는 1,329 다(`scratch/lab/measure_overhead.py`). 과대 예약은 히스토리 예산을
        // 조용히 깎으므로, 실측 밑으로 내려가지 않으면서 두 배를 넘지도 않게 묶는다.
        assertTrue(
            "예약 ${Constants.PREFILL_OVERHEAD_TOKENS} 이 실측 $MEASURED_OVERHEAD 보다 작다",
            Constants.PREFILL_OVERHEAD_TOKENS >= MEASURED_OVERHEAD
        )
        assertTrue(
            "예약이 실측의 두 배를 넘으면 히스토리 예산을 과도하게 깎는다",
            Constants.PREFILL_OVERHEAD_TOKENS <= MEASURED_OVERHEAD * 2
        )
    }

    @Test
    fun `툴 결과 예산이 생성 여유를 넘지 않는다`() {
        // [WHY] 툴 결과는 턴 중간에 KV 로 들어가 어떤 예산 검사도 거치지 않는다 — 툴 회신 턴은
        // 재생성 금지 턴이라(0.8.5 가드) 완전한 보장이 구조적으로 불가능하다(ADR-020). 이 관계는
        // "초과 폭이 생성 여유와 같은 자릿수를 넘지 않게" 묶는 것이고, 툴 루프 상한(3회)이 이
        // 값을 곱한다는 것도 함께 기억할 것. 예전 캡은 ko 5,000자 ≈ 2,500토큰이었다.
        assertTrue(
            "툴 결과 예산 ${Constants.TOOL_RESULT_MAX_TOKENS} 이 생성 여유 ${Constants.GENERATION_HEADROOM_TOKENS} 를 넘는다",
            Constants.TOOL_RESULT_MAX_TOKENS <= Constants.GENERATION_HEADROOM_TOKENS
        )
    }

    @Test
    fun `툴 결과 예산에서 래퍼 예약을 빼도 본문이 남는다`() {
        // [WHY] 래퍼 예약(JSON 골격 + 근거 고정 지침)이 예산을 다 먹으면 위키 본문이 표식만
        // 남는 수준으로 잘려 검색이 사실상 무력화된다.
        val body = Constants.TOOL_RESULT_MAX_TOKENS - Constants.TOOL_RESULT_ENVELOPE_RESERVE_TOKENS
        assertTrue("본문 예산이 $body 토큰뿐이다", body >= 300)
    }

    private companion object {
        /** `Conversation.token_count` 실측 — 시스템 지시 573 + 툴 5종 652 + few-shot 104. */
        const val MEASURED_OVERHEAD = 1329
    }
}
