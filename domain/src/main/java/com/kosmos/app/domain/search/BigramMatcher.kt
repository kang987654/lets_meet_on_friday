package com.kosmos.app.domain.search

import java.util.Locale

/**
 * [BigramMatcher]
 * 문자 바이그램(한글·숫자) + 소문자 단어/어간 토큰(라틴 알파벳) 겹침으로 질의 항과 문서의 어휘 유사도를 매깁니다 (C1+C′3, 0.25.0, 0.40.0 M1).
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
 * [WHY] 한글은 조사·어미가 붙어("회의를", "회의에서") 어절 단위 토큰이 불안정하고, 2글자 창은 "회의"를 보존한다.
 * 반면 영어(라틴 알파벳)는 공백을 지우고 2글자씩 자르면("mybikepassword" → "my", "yb", "ik", "ep") 단어 경계를 넘는 조각과
 * "th"·"er"·"in" 같은 흔한 2글자가 무관한 영어 메모까지 높은 점수를 주므로(0.40.0 2차 검토), 라틴 단어는 단어 경계를 넘지 않는
 * 소문자 단어/어간 토큰으로 매칭한다. 한글·숫자만 있는 한국어 문서는 기존 2글자 창과 100% 동일하다.
 */
object BigramMatcher {

    private val LATIN_WORD = Regex("[a-zA-Z]{2,}")
    private val EN_STOPWORDS = setOf(
        "the", "and", "for", "that", "this", "with", "from", "are", "was", "were",
        "have", "has", "had", "what", "where", "when", "who", "how", "why", "into",
        "onto", "about", "your", "our", "their", "his", "her", "its"
    )

    /**
     * 한글·숫자는 공백·구두점을 지운 2글자 창을 내고, 라틴 알파벳이 들어 있으면 단어 경계를 넘지 않는 소문자 단어/어간 토큰으로 뽑습니다.
     */
    fun bigrams(text: String): Set<String> {
        val hasLatin = text.any { it in 'a'..'z' || it in 'A'..'Z' }
        if (!hasLatin) {
            val compact = buildString(text.length) {
                for (ch in text) if (ch.isLetterOrDigit()) append(ch)
            }
            if (compact.length < 2) return emptySet()
            val out = HashSet<String>(compact.length)
            for (i in 0 until compact.length - 1) out.add(compact.substring(i, i + 2))
            return out
        }

        val out = HashSet<String>(text.length)
        // 1. 비-라틴(한글·숫자) 구간은 기존처럼 연속 2글자 창을 낸다 ("B2 구역 117번", "자전거 4821").
        val nonLatinCompact = buildString(text.length) {
            for (ch in text) {
                if (ch.isLetterOrDigit() && ch !in 'a'..'z' && ch !in 'A'..'Z') append(ch)
            }
        }
        for (i in 0 until nonLatinCompact.length - 1) {
            out.add(nonLatinCompact.substring(i, i + 2))
        }

        // 2. 영숫자 혼합 코드("B2", "C14", "K20391", "sky7788") 보존을 위해 공백/기호로 나뉜 영숫자 덩어리 중 숫자가 섞인 것도 2글자 창으로 넣는다.
        for (chunk in text.split(NON_ALNUM)) {
            if (chunk.length >= 2 && chunk.any { it.isDigit() } && chunk.any { it in 'a'..'z' || it in 'A'..'Z' }) {
                val lower = chunk.lowercase(Locale.ROOT)
                for (i in 0 until lower.length - 1) out.add(lower.substring(i, i + 2))
            }
        }

        // 3. 순수 라틴 단어는 소문자 단어 + 어간 토큰(`word:xxx`)으로 넣는다 — 단어 경계를 넘는 2글자나 "th"/"er" 오염을 막는다.
        for (m in LATIN_WORD.findAll(text)) {
            val w = m.value.lowercase(Locale.ROOT)
            if (w in EN_STOPWORDS) continue
            out.add("w:$w")
            val stem = stemEnglish(w)
            if (stem != w && stem.length >= 3) out.add("w:$stem")
        }
        return out
    }

    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

    private fun stemEnglish(w: String): String = when {
        w.length > 5 && w.endsWith("ing") -> w.dropLast(3).let { if (it.length >= 4 && it.last() == it[it.length - 2]) it.dropLast(1) else it }
        w.length > 5 && w.endsWith("ic") -> w.dropLast(2)
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 4 && w.endsWith("es") -> w.dropLast(2)
        w.length > 4 && w.endsWith("ed") -> w.dropLast(2)
        w.length > 4 && w.endsWith("y") -> w.dropLast(1)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
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

    /**
     * 두 텍스트의 대칭 겹침 `|A∩B| / min(|A|,|B|)` (0~1) — 기억 중복 후보 판정용(0.30.0).
     *
     * [WHY] Jaccard 가 아니라 containment 다 — 같은 사실을 짧게/길게 적은 쌍("여동생 생일 5월 3일" vs "여동생 생일은
     * 5월 3일")에서 Jaccard 는 길이 차이만큼 깎인다. exp42: containment 0.3 이 참 중복 8/8, Jaccard 0.3 은 7/8.
     */
    fun containment(a: String, b: String): Double {
        val x = bigrams(a)
        val y = bigrams(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.count { it in y }.toDouble() / minOf(x.size, y.size)
    }

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
