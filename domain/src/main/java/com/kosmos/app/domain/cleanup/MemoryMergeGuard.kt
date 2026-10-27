package com.kosmos.app.domain.cleanup

import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.search.BigramMatcher

/**
 * 기억 병합의 **결정적 가드** — 모델 판정 앞뒤에서 같은 사실일 수 없는 쌍과 정보를 잃은 병합을 걸러냅니다 (0.30.0).
 *
 * [WHY] exp42: 바이그램 겹침만으로 후보를 내면 모델이 "자전거 자물쇠 번호 4821 + 현관 비밀번호 4821" 을 같음으로 합쳤다.
 * 병합은 기억을 지우므로 모델 판단에 맡기기 전에 규칙으로 막을 수 있는 것은 막는다(exp42b: 숫자·대상 함정이 후보 단계에서 빠짐).
 */
object MemoryMergeGuard {

    /** 숫자를 뺀 본문 겹침 하한 — exp42b 에서 참 중복 8/8 을 유지하는 가장 높은 값. */
    const val MIN_TEXT_OVERLAP = 0.3

    private val digits = Regex("\\d+")

    /** 텍스트 속 숫자 묶음 집합("5월 3일" → {5, 3}). */
    fun numbersIn(text: String): Set<String> = digits.findAll(text).map { it.value }.toSet()

    /**
     * 같은 사실일 **수도 있는** 쌍인가 — ① 숫자 집합이 같고(4821 vs 4812 는 다른 사실) ② 숫자를 뺀 본문이 충분히 겹친다(대상이 같아야).
     */
    fun isCandidate(a: String, b: String): Boolean {
        if (numbersIn(a) != numbersIn(b)) return false
        return BigramMatcher.containment(digits.replace(a, ""), digits.replace(b, "")) >= MIN_TEXT_OVERLAP
    }

    /** 노트 목록에서 후보 쌍(인덱스)을 냅니다. */
    fun candidatePairs(notes: List<KnowledgeNote>): List<Pair<Int, Int>> =
        notes.indices.flatMap { i ->
            (i + 1 until notes.size).mapNotNull { j ->
                if (isCandidate(notes[i].content, notes[j].content)) i to j else null
            }
        }

    /**
     * 합친 문장의 끝을 메모 말투로 — "…4821이다." → "…4821", "있다" → "있음", "마신다" → "마심", "먹는다" → "먹음", "했다" → "했음",
     * "가깝다" → "가까움", "길다" → "긺". 받침 없는 글자 + "다"("회사는 판교다")는 명사 + 서술격일 수 있어 그대로 둔다("판굠"이 되지 않게).
     *
     * [WHY] 판정 프롬프트로는 해라체 끝맺음을 못 바꿨다("…이다로 끝내지 마세요"를 넣어도 6/7, exp59 J1). 프롬프트는 오병합 실측(exp42) 원문 그대로 두고
     * 끝만 코드로 정리한다 — 합친 문장은 메모 본문이 되어 화면과 기억 검색 결과에 그대로 보인다. 동사 끝은 0.38.9 M4.
     */
    fun toMemoEnding(merged: String): String {
        val text = merged.trim().trimEnd('.', '。').trimEnd()
        if (!text.endsWith("다") || text.length < 2) return text
        L_IRREGULAR.entries.firstOrNull { (from, _) -> text.endsWith(from) && isWordStart(text, text.length - from.length) }
            ?.let { (from, to) -> return text.dropLast(from.length) + to }
        return when {
            text.endsWith("이다") && text.length > 2 -> text.dropLast(2).trimEnd()
            text.endsWith("아니다") -> text.dropLast(3) + "아님"
            text.endsWith("는다") && text.length > 2 -> text.dropLast(2) + "음"
            else -> {
                val stem = text[text.length - 2]
                val jong = jongOf(stem) ?: return text
                val head = text.dropLast(2)
                when (jong) {
                    0 -> text
                    JONG_N -> head + withJong(stem, JONG_M)
                    JONG_B -> head + withJong(stem, 0) + "움"
                    JONG_L -> head + withJong(stem, JONG_LM)
                    else -> head + stem + "음"
                }
            }
        }
    }

    // [WHY] ㄹ 탈락 동사는 "…ㄴ다"만 보면 받침 ㄹ 이 사라진 뒤라 "산다" → "삼"이 된다 — 자주 쓰는 것만 낱말째 바꾼다.
    private val L_IRREGULAR = linkedMapOf(
        "만든다" to "만듦", "산다" to "삶", "안다" to "앎", "판다" to "팖", "논다" to "놂", "운다" to "욺", "연다" to "엶", "분다" to "붊", "건다" to "걺"
    )
    private const val HANGUL_BASE = 0xAC00
    private const val JONG_N = 4
    private const val JONG_L = 8
    private const val JONG_LM = 10
    private const val JONG_M = 16
    private const val JONG_B = 17

    private fun jongOf(c: Char): Int? = (c.code - HANGUL_BASE).takeIf { it in 0 until 11172 }?.rem(28)

    private fun withJong(c: Char, jong: Int): Char = (c.code - (c.code - HANGUL_BASE) % 28 + jong).toChar()

    private fun isWordStart(text: String, index: Int): Boolean = index == 0 || text[index - 1].isWhitespace()

    /** 합친 문장이 원문들의 숫자를 하나도 잃지 않았는가 — 잃었으면 제안에서 뺀다. */
    fun keepsNumbers(merged: String, sources: List<String>): Boolean =
        numbersIn(merged).containsAll(sources.flatMap { numbersIn(it) }.toSet())
}
