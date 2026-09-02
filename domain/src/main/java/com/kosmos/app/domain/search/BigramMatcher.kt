package com.kosmos.app.domain.search

/**
 * [BigramMatcher]
 * 문자 바이그램 겹침으로 질의 항과 문서의 어휘 유사도를 매깁니다 (C1+C′3, 0.25.0).
 *
 * ### Architecture Context
 * - **Layer**: Domain (Search) — 순수 함수, 의존성 없음
 * - **Consumers**: `SearchMemoryToolExecutor`(랭킹·미스 시 전수 스캔), `DrawerViewModel`(드로어 검색)
 *
 * [WHY] exp33(2026-08-15)의 `score()` **원문 포팅**이다 — 그 실험의 recall@1 16/16 은 이 점수 함수로
 * 잰 수치라, 다른 랭킹(FTS5 bm25 등)으로 바꾸면 측정이 무효가 된다. 인메모리인 이유: Room 에 `@Fts5`
 * 가 없고 FTS5 내장 토크나이저에 바이그램이 없어 가상 테이블은 동기화·가용성 비용이 크며, 개인 1인
 * 규모(문서 수백)에서 전수 스캔은 수 ms 다(BigramMatcherTest 2,000 문서 계측). FTS5 는 규모 게이트
 * (문서 2,000+ 또는 스캔 50ms+)에서 다시 본다 — ADR-025.
 *
 * [WHY] 바이그램(2글자 창)인 이유: 한국어는 조사·어미가 붙어("회의를", "회의에서") 어절 단위 토큰이
 * 불안정하고, 2글자 창은 "회의"를 보존한다. 3글자 창(FTS5 trigram)은 2글자 질의("회의")를 못 다룬다.
 */
object BigramMatcher {

    /**
     * 공백·구두점·기호를 지우고 2글자 창을 낸다 — exp33 `re.sub(r"[\s\W_]+", "", s)` 미러
     * (문자·숫자만 남긴다). 1글자 이하면 빈 집합.
     */
    fun bigrams(text: String): Set<String> {
        val compact = buildString(text.length) {
            for (ch in text) if (ch.isLetterOrDigit()) append(ch)
        }
        if (compact.length < 2) return emptySet()
        val out = HashSet<String>(compact.length)
        for (i in 0 until compact.length - 1) out.add(compact.substring(i, i + 2))
        return out
    }

    /**
     * 항별 `|항∩문서| / |항|` 의 합 — 항이 많이 맞을수록, 항 하나가 온전히 들어 있을수록 크다.
     * 바이그램이 없는 항(1글자)은 0 으로 센다(exp33 과 동일).
     */
    fun score(terms: List<String>, docText: String): Double = score(terms, bigrams(docText))

    /** 문서 바이그램을 미리 계산해 둔 호출자용 — 전수 스캔에서 문서 한 번만 자르게. */
    fun score(terms: List<String>, docBigrams: Set<String>): Double {
        var total = 0.0
        for (term in terms) {
            val b = bigrams(term)
            if (b.isEmpty()) continue
            total += b.count { it in docBigrams }.toDouble() / b.size
        }
        return total
    }

    /**
     * 가장 잘 맞은 항 하나의 겹침 비율(0~1) — 전수 스캔의 **최소 임계** 판정용.
     *
     * [WHY] 합계 점수는 항이 많으면 잡음이 누적된다. "이 문서가 질의의 어느 한 항이라도 제대로
     * 담고 있는가"를 따로 물어야 "좋아하는 것"(겹침 0.33) 같은 질의가 아무 문서나 끌어오지 않고
     * 기존 태그 목록 폴백으로 떨어진다(MemoryPipelineIntegrationTest 계약).
     */
    fun bestTermOverlap(terms: List<String>, docText: String): Double =
        bestTermOverlap(terms, bigrams(docText))

    fun bestTermOverlap(terms: List<String>, docBigrams: Set<String>): Double {
        var best = 0.0
        for (term in terms) {
            val b = bigrams(term)
            if (b.isEmpty()) continue
            val overlap = b.count { it in docBigrams }.toDouble() / b.size
            if (overlap > best) best = overlap
        }
        return best
    }
}
