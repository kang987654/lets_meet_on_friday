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
    fun `오버헤드 예약이 프로필 상한까지 포함해 실측을 덮는다`() {
        // [WHY] 프로필(C′1)은 상시 주입이라 최악(상한 꽉 채움)에도 예약 안에 들어야 한다 —
        // 재원은 few-shot 제거(104토큰, exp35). 이 관계가 깨지면 프로필이 히스토리 예산을
        // 조용히 침식한다.
        assertTrue(
            "예약 ${Constants.PREFILL_OVERHEAD_TOKENS} < 실측 $MEASURED_OVERHEAD + 프로필 상한 ${Constants.PROFILE_MAX_TOKENS}",
            Constants.PREFILL_OVERHEAD_TOKENS >= MEASURED_OVERHEAD + Constants.PROFILE_MAX_TOKENS
        )
    }

    @Test
    fun `오버헤드 예약이 실측값보다 크고 과하지 않다`() {
        // [WHY] 2600 이었고 주석은 "툴 선언만 ~2천 토큰" 이라고 적었는데 둘 다 틀렸다. 실측
        // 합계는 1,329 다(`scratch/lab/measure_overhead.py`). 과대 예약은 히스토리 예산을
        // 조용히 깎으므로, 실측 밑으로 내려가지 않으면서 두 배를 넘지도 않게 묶는다.
        // 하한(예약 ≥ 실측)은 위 테스트의 "예약 ≥ 실측 + 프로필 상한"에서 따라 나온다 — 상한만 본다.
        assertTrue(
            "예약이 실측의 두 배를 넘으면 히스토리 예산을 과도하게 깎는다",
            Constants.PREFILL_OVERHEAD_TOKENS <= MEASURED_OVERHEAD * 2
        )
    }

    @Test
    fun `예산 상한이 GPU FP32 기기 실측 온전선 아래다`() {
        // [WHY] 0.37.0(사용자 확인 후 변경): 처방이 "예산을 FP16 발병점(1,854) 아래로"에서 "GPU 를 FP32 로"로 바뀌었다(ADR-029).
        // FP32 도 무한히 안전한 것은 아니다 — 기기에서 온전함을 확인한 깊이까지만 슬라이더를 연다. 더 올리려면 그 깊이를 먼저 잰다.
        assertTrue(
            "예산 상한 ${Constants.MAX_CONTEXT_TOKENS} 이 FP32 기기 실측 온전선 ${Constants.GPU_FP32_VERIFIED_SAFE_TOKENS} 을 넘는다",
            Constants.MAX_CONTEXT_TOKENS <= Constants.GPU_FP32_VERIFIED_SAFE_TOKENS
        )
        assertTrue(
            "발병 경계 실측값의 순서가 뒤집혔다",
            Constants.GPU_DIGIT_ONSET_SAFE_TOKENS < Constants.GPU_DIGIT_ONSET_BROKEN_TOKENS
        )
    }

    @Test
    fun `예산 상한에 툴 결과를 얹어도 프리필 천장을 넘지 않는다`() {
        // [WHY] 툴 결과는 재설정 검사 없이 턴 중간에 KV 로 들어간다(ADR-020). 대화가 예산 상한까지 자란 턴에 툴 결과가 붙어도
        // 생성 여유가 남아야 한다 — 예산 1,700 시절엔 여유가 넉넉해 따로 묶지 않았다.
        assertTrue(
            "예산 상한 ${Constants.MAX_CONTEXT_TOKENS} + 툴 결과 ${Constants.TOOL_RESULT_MAX_TOKENS} > 천장 ${Constants.PREFILL_CEILING_TOKENS}",
            Constants.MAX_CONTEXT_TOKENS + Constants.TOOL_RESULT_MAX_TOKENS <= Constants.PREFILL_CEILING_TOKENS
        )
    }

    @Test
    fun `슬라이더 범위는 하한 기본값 상한 순이고 하한에서도 히스토리가 남는다`() {
        assertTrue(Constants.MIN_CONTEXT_TOKENS <= Constants.DEFAULT_CONTEXT_TOKENS)
        assertTrue(Constants.DEFAULT_CONTEXT_TOKENS <= Constants.MAX_CONTEXT_TOKENS)
        assertTrue(
            "하한 ${Constants.MIN_CONTEXT_TOKENS} 에서 오버헤드 예약 뒤 히스토리가 최소치 아래다",
            Constants.MIN_CONTEXT_TOKENS - Constants.PREFILL_OVERHEAD_TOKENS >= Constants.MIN_HISTORY_TOKENS
        )
        assertEquals("슬라이더가 100 단위로 나뉘어야 한다", 0, (Constants.MAX_CONTEXT_TOKENS - Constants.MIN_CONTEXT_TOKENS) % 100)
    }

    @Test
    fun `재설정 하한이 재생성 루프를 만들지 않는다`() {
        // [WHY] 갓 재생성한 대화의 프리필 ≈ 오버헤드 + 최소 히스토리다. 하한이 그보다 작으면
        // 재생성 직후 이미 임계값을 넘어 **매 턴 재생성** 루프가 된다 — 예산을 발병점 아래로
        // 내리면서(1700) 이 바닥에 정확히 닿았으므로 관계로 고정한다.
        assertTrue(
            "하한 ${Constants.MIN_CONVERSATION_RESET_TOKENS} < 오버헤드+최소히스토리 ${Constants.PREFILL_OVERHEAD_TOKENS + Constants.MIN_HISTORY_TOKENS}",
            Constants.MIN_CONVERSATION_RESET_TOKENS >= Constants.PREFILL_OVERHEAD_TOKENS + Constants.MIN_HISTORY_TOKENS
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

    @Test
    fun `첨부 문서가 래퍼를 포함해도 최소 히스토리 예산에 살아남는다`() {
        // [WHY] 예전 캡 2500자는 예산 6000 시절의 유물이었다 — 문서 메시지의 추정 토큰이
        // 히스토리 예산(300)을 넘으면 슬라이딩 윈도우에서 **통째로 탈락**해, 문서에 대해 이어
        // 물으면 모델이 문서를 본 적 없는 상태가 된다(MVP 감사 A3). 캡 상수만 보지 않고 실제
        // 저장 형태(래퍼 포함)를 추정기에 넣어 확인한다 — 래퍼가 자라면 여기서 걸린다.
        val tokenizer = com.kosmos.app.runtime.gemma.GemmaTokenizer()
        val storedForm = "[Attached Document]\n\"\"\"\n" +
            "첨부된 문서 내용(document.txt):\n" +
            "가".repeat(Constants.MAX_ATTACHED_DOC_CHARS) +
            "\n\"\"\""
        // 슬라이딩 윈도우가 메시지마다 더하는 태그 오버헤드 10 을 포함한다 (ContextBuilder).
        val estimated = tokenizer.sizeInTokens(storedForm) + 10
        assertTrue(
            "문서 메시지 추정 $estimated 토큰 > 최소 히스토리 ${Constants.MIN_HISTORY_TOKENS} — 윈도우에서 탈락한다",
            estimated <= Constants.MIN_HISTORY_TOKENS
        )
    }

    private companion object {
        /**
         * `Conversation.token_count` 실측 — 시스템 지시 + 툴 6종 선언 합계. 이력:
         * 1,329(5종+few-shot) → +52(add_reminder 다이어트 후, exp34b) → −104(few-shot 제거,
         * `scratch/lab/exp35_profile_budget.py` 2026-08-28) = 1,277 → +16(날짜 없는 시각 = 오늘,
         * `scratch/lab/exp39c_date_carryover.py` 2026-10-01) = 1,293 → −67(중복 문구 다이어트,
         * `scratch/lab/exp41_prompt_diet.py` 2026-10-01) = 1,226 → +24(턴 리마인더 긍정 답 문장 + `[Now]`,
         * `scratch/lab/exp49b_overhead.py` 2026-10-13 — tokenize 차이와 token_count 차이가 같다) = 1,250 → +19(말투 줄
         * "Reply briefly in polite 해요체…", `scratch/lab/exp55b_overhead.py` 2026-10-22 — tokenize +18, token_count +19 중 큰 값) = 1,269
         * → +68(`list_reminders` 선언 + 트리거 줄, `scratch/lab/exp56b_overhead.py` 2026-10-23 — token_count 1,349 → 1,417; tokenize 규칙 +31·선언 +19 에
         * 선언 템플릿이 더해진다) = 1,337. 예약은 같은 회차에 1,400 → 1,450(사용자 결정). → +12(역할 줄 "…who also chats, recommends and gives advice…",
         * `scratch/lab/exp58c_checks.py` 2026-10-23 — token_count 1,418 → 1,430, tokenize +12) = 1,349. 예약 1,450 → 1,500(사용자 결정, 0.38.6 M4). → +16(add_memory 트리거 "사실만 말하면 확인 + 메모해 둘까요?",
         * `scratch/lab/exp62f_overhead.py` 2026-10-26 — token_count 1,430 → 1,446, tokenize +16) = **1,365**.
         * 프로필 블록은 여기 없고 [Constants.PROFILE_MAX_TOKENS] 상한으로 따로 계상된다.
         */
        const val MEASURED_OVERHEAD = 1365
    }
}
