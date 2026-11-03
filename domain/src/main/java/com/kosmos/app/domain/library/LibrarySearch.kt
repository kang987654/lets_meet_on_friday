package com.kosmos.app.domain.library

import com.kosmos.app.domain.search.BigramMatcher
import kotlin.math.ln

/**
 * 검색 후보 — 색인이 돌려준 조각과 그 조각의 문서.
 */
data class LibraryCandidate(val documentName: String, val chunk: LibraryChunk, val documentId: String = "")

/**
 * [LibrarySearch]
 * 문서 보관함 검색의 순수 계산부입니다 (0.41.0 M1) — 색인 글자 만들기, 색인 질의 고르기, 후보 재순위.
 *
 * ### Architecture Context
 * - **Layer**: Domain (Library) — 저장소 구현(data)이 FTS4 질의·결과를 이 함수들로 만든다. DB·안드로이드 의존 없음.
 *
 * ### Key Flow
 * 1. 색인: 조각의 머리말+본문을 [BigramMatcher.bigrams] 로 쪼개 공백으로 이은 글자를 FTS4(`simple` 토크나이저)에 넣는다([indexText]).
 * 2. 질의: 질의 바이그램 중 문서빈도가 전체의 [MAX_DF_RATIO] 이하인 것만 `OR` 로 잇는다([matchQuery]) — "민법"·"하여" 같은 흔한
 *    2글자는 수천 행을 끌어오기만 한다. 남는 게 없으면 가장 드문 몇 개를 쓴다.
 * 3. 후보: 매치 행마다 맞은 바이그램의 idf 합으로 상위 [CANDIDATES] 개([candidateScore]).
 * 4. 재순위: `BigramMatcher.score` 로 머리말 ×1.5 + 본문, 조문 번호 일치 +3, 문서 이름 일치 +1([rank]).
 *
 * [WHY] 인메모리 전수 스캔(기억 검색 — ADR-025)은 책 한 권(조각 수천·바이그램 수십만)에서 메모리가 먼저 무너진다. FTS4 바이그램은
 * 64만 자 4.7MB·질의 2~5ms(PC)로 포스팅 테이블(5.9MB·17~19ms)보다 작고 빨랐다(exp74). FTS5 와 한국어 토크나이저 문제는 바이그램을
 * 미리 잘라 넣어 피한다. 재순위 점수는 기억 검색과 같은 함수라 exp33·exp74 측정과 같은 기준이다(IDF 가중은 차이 없었다).
 */
object LibrarySearch {

    const val CANDIDATES = 100
    const val MAX_DF_RATIO = 0.2
    private const val FALLBACK_TERMS = 3
    private const val PATH_WEIGHT = 1.5
    private const val ARTICLE_BONUS = 3.0
    private const val NAME_BONUS = 1.0

    private val ARTICLE_IN_QUERY = Regex("""(\d+)\s*조(?:\s*의\s*(\d+))?""")

    /**
     * FTS4 토큰 — `simple` 토크나이저는 영숫자·비ASCII 만 토큰 글자로 보므로 [BigramMatcher] 의 라틴 단어 표지 `w:` 의 콜론을 뺀다.
     */
    fun tokens(text: String): Set<String> = BigramMatcher.bigrams(text).mapTo(HashSet()) { it.replace(":", "") }

    /** 조각을 색인에 넣을 글자 — 정렬해 결정적으로 만든다(같은 조각이면 같은 글자). */
    fun indexText(chunk: LibraryChunk): String = tokens(chunk.sectionPath + " " + chunk.text).sorted().joinToString(" ")

    /** 질의의 조문 번호 — "제162조"·"162 조"·"제5조의2" → `"162"`·`"5의2"`. 없으면 null. */
    fun articleOf(query: String): String? =
        ARTICLE_IN_QUERY.find(query)?.let { m -> m.groupValues[1] + m.groupValues[2].takeIf { it.isNotEmpty() }?.let { "의$it" }.orEmpty() }

    /**
     * 색인 질의에 쓸 토큰 — 문서빈도([df], 토큰 → 그 토큰을 가진 조각 수)가 [total] 의 [MAX_DF_RATIO] 이하인 것.
     * 모두 흔하면 가장 드문 [FALLBACK_TERMS] 개. 색인에 없는 토큰은 뺀다. 남는 게 없으면 빈 목록.
     */
    fun queryTerms(query: String, df: Map<String, Int>, total: Int): List<String> {
        val present = tokens(query).filter { (df[it] ?: 0) > 0 }.sorted()
        val rare = present.filter { (df[it] ?: 0) <= MAX_DF_RATIO * total }
        return rare.ifEmpty { present.sortedBy { df[it] ?: 0 }.take(FALLBACK_TERMS) }
    }

    /** FTS4 `MATCH` 식 — 토큰이 영숫자·한글뿐이라 따옴표·연산자 이스케이프가 필요 없다. */
    fun matchQuery(terms: List<String>): String = terms.joinToString(" OR ")

    /** 토큰의 idf — 드물수록 크다. */
    fun idf(df: Int, total: Int): Double = ln((total + 1.0) / (df + 1.0)) + 1.0

    /**
     * FTS4 `matchinfo(t, 'x')` 결과로 후보 점수 — 구(토큰)마다 정수 3개(이 행의 적중, 전체 적중, 적중 행 수)이고 열이 하나다.
     * 이 행에 나온 토큰의 idf 합.
     */
    fun candidateScore(matchinfo: IntArray, termIdf: List<Double>): Double {
        var score = 0.0
        for (p in termIdf.indices) {
            if (matchinfo.getOrElse(3 * p) { 0 } > 0) score += termIdf[p]
        }
        return score
    }

    /** `matchinfo` BLOB(리틀 엔디언 32비트 부호 없는 정수 배열) → 정수 배열. */
    fun decodeMatchinfo(blob: ByteArray): IntArray = IntArray(blob.size / 4) { i ->
        (blob[4 * i].toInt() and 0xFF) or
            ((blob[4 * i + 1].toInt() and 0xFF) shl 8) or
            ((blob[4 * i + 2].toInt() and 0xFF) shl 16) or
            ((blob[4 * i + 3].toInt() and 0xFF) shl 24)
    }

    /** 후보를 점수 내림차순으로 — 같은 점수면 들어온 순서(색인 후보 순위)를 지킨다. */
    fun rank(query: String, candidates: List<LibraryCandidate>): List<LibraryCandidate> {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val wantArticle = articleOf(query)
        val compactQuery = query.replace(" ", "")
        return candidates
            .map { c ->
                var s = PATH_WEIGHT * BigramMatcher.score(terms, c.chunk.sectionPath) + BigramMatcher.score(terms, c.chunk.text)
                if (wantArticle != null && c.chunk.article == wantArticle) s += ARTICLE_BONUS
                if (c.documentName.replace(" ", "").let { it.isNotEmpty() && compactQuery.contains(it) }) s += NAME_BONUS
                c to s
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
