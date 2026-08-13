package com.kosmos.app.runtime.gemma

import android.content.Context
import com.kosmos.app.domain.tool.Tokenizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [GemmaTokenizer]
 * 슬라이딩 윈도우 예산 배분용 토큰 수 추정기입니다.
 *
 * ### Architecture Context
 * - **Layer**: Runtime (`:app`)
 * - **Dependencies**: 없음 (문자 클래스 휴리스틱)
 *
 * [WHY] LiteRT-LM AAR 은 stateless `sizeInTokens(String)` API 를 제공하지 않아 추정치를 쓴다.
 * 추정이 **과소평가면 안 된다** — 슬라이딩 윈도우가 실제보다 많은 히스토리를 실어 프리필이
 * 엔진 KV 용량(4096)을 넘고, AAR 은 초과를 오류 없이 진행하므로(exp17) 그 결과는 크래시가
 * 아니라 품질 저하(글자 깨짐 후보)로만 드러난다 (ADR-020).
 *
 * [WHY] 예전 추정식 `length / 3 + 1` 은 영어 산문에만 맞는 값이었다. exp26 실측
 * (`scratch/lab/exp26_token_ratio.py`, `engine.tokenize` 직접 측정):
 * - 한국어 채팅: 1.48~1.70자/토큰 → 예전 추정이 실측의 **0.52~0.60배** (약 1.7~1.9배 과소)
 * - 숫자·ISO 문자열: **1.00자/토큰** ("2026-08-17T10:00:00" = 19자 = 19토큰) → **0.35배**
 * - 영어 산문: 4.65자/토큰 → 1.57배 과대 (이쪽만 과대였다)
 *
 * [WHY] 문자 3클래스로 나눈다 — ASCII 를 한 클래스로 묶을 수 없다. 영어 산문(~4.6자/토큰)과
 * 숫자·기호(1.0자/토큰)가 같은 ASCII 인데 밀도가 4배 넘게 갈린다. 계수는 exp26 격자 탐색에서
 * **전 14샘플 과소평가 0건 AND 과대 1.5배 이내**를 만족하는 최선값(최악 과대 1.46배, zh):
 * 영문·공백 4.25자/토큰, 그 외 ASCII 0.9자/토큰, 비ASCII 1.2자/토큰. 중국어도 비ASCII
 * 클래스가 덮으므로 언어별 분기가 필요 없다.
 *
 * [WHY] 과대평가의 대가는 히스토리 축소(전형 1.1~1.3배)다 — 초과의 대가(무음 품질 저하)보다
 * 싸다. 권위 있는 최종 방어선은 런타임의 실토큰 재설정 판정([GemmaModelRunner])이고, 이
 * 추정기는 윈도우 크기 결정만 맡는다.
 */
@Singleton
class GemmaTokenizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val runtimeManager: GemmaRuntimeManager
) : Tokenizer {

    override fun sizeInTokens(text: String): Int = estimateTokens(text)

    private fun estimateTokens(text: String): Int {
        var alphaSpace = 0
        var asciiOther = 0
        var nonAscii = 0
        for (c in text) {
            when {
                c.code >= 128 -> nonAscii++
                c.isLetter() || c == ' ' -> alphaSpace++
                else -> asciiOther++
            }
        }
        return (alphaSpace / CHARS_PER_TOKEN_ALPHA +
            asciiOther / CHARS_PER_TOKEN_ASCII_OTHER +
            nonAscii / CHARS_PER_TOKEN_NON_ASCII).toInt() + 1
    }

    private companion object {
        // exp26 실측 계수. 바꾸려면 scratch/lab/exp26_token_ratio.py 의 게이트(과소평가 0건)를
        // 다시 통과해야 한다 — GemmaTokenizerTest 가 실측 픽스처로 같은 게이트를 고정한다.
        const val CHARS_PER_TOKEN_ALPHA = 4.25
        const val CHARS_PER_TOKEN_ASCII_OTHER = 0.9
        const val CHARS_PER_TOKEN_NON_ASCII = 1.2
    }
}
