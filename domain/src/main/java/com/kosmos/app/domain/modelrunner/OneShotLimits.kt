package com.kosmos.app.domain.modelrunner

import com.kosmos.app.core.common.AppLanguage

/**
 * 부수 계산(oneShot) 출력 토큰 상한 (0.42.4 M2, [ChatPrompt.maxOutputTokens]).
 *
 * [WHY] 휴대폰에서 회고·판정이 깨진 글자를 엔진 한도까지(약 3,800토큰, 2분) 냈다. 정상 출력의 몇 배로 막는다 —
 * 회고 약 100 · 요약 문서 한 편 약 150(두 주제면 두 편) · 추출 약 60 · 판정 3~40 토큰(exp96·97, 휴대폰 bench).
 */
object OneShotLimits {
    const val SUMMARY = 800
    const val EXTRACT = 300
    const val REVIEW = 400
    const val JUDGE = 120
}

/**
 * 부수 계산 결과가 깨진 글자인가 — 한국어 결과에 한글이 거의 없으면 깨진 것으로 본다(0.42.4 M2).
 *
 * [WHY] 휴대폰에서 깨진 회고("cist(파 21.dinizadi 62.c,gr6000001020stx…")가 그대로 기억으로 저장됐다. 영어 결과는 이 규칙으로 가를 수 없어 보지 않는다(상한이 막는다).
 */
object BrokenOutput {
    private const val MIN_LETTERS = 20
    private const val MIN_HANGUL_SHARE = 0.3

    fun looksBroken(text: String, lang: AppLanguage): Boolean {
        if (lang == AppLanguage.EN) return false
        var hangul = 0
        var latin = 0
        for (c in text) {
            when {
                c in '가'..'힣' -> hangul++
                c in 'a'..'z' || c in 'A'..'Z' -> latin++
            }
        }
        val letters = hangul + latin
        return letters >= MIN_LETTERS && hangul.toDouble() / letters < MIN_HANGUL_SHARE
    }
}
