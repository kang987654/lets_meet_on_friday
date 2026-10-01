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

    /** 합친 문장이 원문들의 숫자를 하나도 잃지 않았는가 — 잃었으면 제안에서 뺀다. */
    fun keepsNumbers(merged: String, sources: List<String>): Boolean =
        numbersIn(merged).containsAll(sources.flatMap { numbersIn(it) }.toSet())
}
