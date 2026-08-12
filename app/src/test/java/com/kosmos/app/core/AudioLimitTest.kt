package com.kosmos.app.core

import com.kosmos.app.core.common.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AudioLimitTest]
 * 오디오 상한이 **공식 문서 값과 토큰 예산 양쪽에 맞는지** 못박습니다.
 *
 * [WHY] Gemma 4 공식 문서(capabilities/audio)는 오디오를 **최대 30초**, 비용을 **초당 25 토큰**
 * 으로 적는다. 앱에는 상한이 아예 없어서 마이크 버튼을 다시 누를 때까지 무한히 녹음됐고, 한계를
 * 넘긴 오디오가 그대로 모델에 들어갔다 — 잘리는지, 오류가 나는지, 엉뚱한 결과가 나오는지
 * 정의되지 않은 상태였다(2026-08-13 문서 대조에서 발견).
 *
 * [WHY] 상한 값 하나만 지키는 것으로는 부족하다. 30초는 750 토큰이고 그것이 프리필 천장 안에
 * 들어가야 전사가 성립한다 — 두 숫자가 서로 어긋나면 상한을 지켜도 추론이 실패한다. 그래서
 * 관계를 함께 고정한다.
 */
class AudioLimitTest {

    @Test
    fun `녹음 상한은 공식 문서의 30초다`() {
        assertEquals(30, Constants.MAX_AUDIO_SECONDS)
    }

    @Test
    fun `30초 오디오의 토큰 비용이 프리필 천장 안에 들어간다`() {
        // [WHY] 전사는 일회성 경로라 툴 선언도 히스토리도 싣지 않는다. 그래서 오디오 토큰 +
        // 전사 시스템 지시만 천장 안에 들어가면 된다.
        val audioTokens = Constants.MAX_AUDIO_SECONDS * TOKENS_PER_SECOND
        assertEquals(750, audioTokens)
        assertTrue(
            "오디오 $audioTokens 토큰이 천장 ${Constants.PREFILL_CEILING_TOKENS} 을 넘는다",
            audioTokens < Constants.PREFILL_CEILING_TOKENS
        )
    }

    @Test
    fun `상한을 올리면 예산 계산을 다시 하도록 여유를 확인한다`() {
        // [WHY] 이 단언이 깨지는 순간 "상한만 올리고 예산은 그대로" 인 상태다. 전사 시스템 지시와
        // 생성분까지 들어갈 여유가 남는지를 함께 본다.
        val audioTokens = Constants.MAX_AUDIO_SECONDS * TOKENS_PER_SECOND
        val headroom = Constants.PREFILL_CEILING_TOKENS - audioTokens
        assertTrue(
            "전사 지시와 출력이 들어갈 여유가 $headroom 토큰밖에 없다",
            headroom >= 1000
        )
    }

    private companion object {
        /** Gemma 4 오디오 비용 — 공식 문서(capabilities/audio) 기준 초당 25 토큰. */
        const val TOKENS_PER_SECOND = 25
    }
}
