package com.kosmos.app.domain.memory

import com.kosmos.app.domain.search.BigramMatcher

/**
 * 기억 검색 결과에서 **직접 저장한 메모와 다른 값을 적은 과거 대화 요약**을 가려냅니다 (0.37.2, 0.39.6 M1, 0.40.0 M2a).
 *
 * [WHY] 요약이 여러 건 다른 값을 적으면 "메모를 믿으라"는 안내가 있어도 모델이 많은 쪽을 따른다(exp52: 4321 요약 3건이면 깊이와 무관하게
 * 메모 134 를 버림). 그런 요약을 "다른 값이 적혀 있다" 한 줄로 줄이면 메모를 답하고 다른 값도 덧붙인다(exp53 조건 C2: 충돌 5/8 → 8/8, 대조 4/4).
 *
 * [WHY] 여러 주제가 섞인 긴 요약이나 범용 낱말("비밀번호", "번호")로 함께 걸린 다른 주제 요약에서 무관한 숫자("회의실 5층", "사물함 214",
 * "송장 6043 1122 9087")가 "다른 값"에 섞여 나갔다(실기기 0.39.3 M4c). 메모에서 숫자와 범용 명사를 뺀 핵심 주제 두 글자 묶음이 있으면
 * 요약 문장(마침표·줄바꿈 기준) 중 그 주제가 겹치는 문장의 숫자만 값으로 본다(0.39.6 M1).
 */
object RecallConflict {

    const val EPISODE_MARKER_KO = "(과거 대화)"
    const val EPISODE_MARKER_EN = "(Past conversation)"

    const val META_NOTE_AT = "noteAt"
    const val META_EPISODE_ID = "episodeId"
    const val META_CONFLICT_COUNT = "conflictCount"
    const val META_OTHER_VALUES = "otherValues"
    const val META_CONFLICT_EPISODE_IDS = "conflictEpisodeIds"

    private val number = Regex("\\d+")

    // [WHY] 숫자 뒤에 날짜·시각·기간 단위가 붙으면 값이 아니다 — "10월 3일 자물쇠 교체"·"Oct 3 at 2 PM" 같은 요약을 충돌로 잘못 줄이지 않게.
    private val dateTimeUnit = Regex(
        "^\\s*(년|월|일|시|분|초|요일|주|개월|시간|(st|nd|rd|th)\\b|\\b(am|pm|a\\.m\\.|p\\.m\\.|o'?clock|years?|months?|days?|hours?|minutes?|seconds?|weeks?)\\b)",
        RegexOption.IGNORE_CASE
    )
    private val monthBeforeNumber = Regex(
        "\\b(jan(uary)?|feb(ruary)?|mar(ch)?|apr(il)?|may|jun(e)?|jul(y)?|aug(ust)?|sep(t(ember)?)?|oct(ober)?|nov(ember)?|dec(ember)?)\\s+$",
        RegexOption.IGNORE_CASE
    )

    // [WHY] "비밀번호"·"번호"·"암호"·"password"·"code"는 여러 주제("자전거 비밀번호", "사물함 번호", "송장번호")에 공통으로 쓰여 주제 구별력이 없다.
    private val genericWords = Regex("비밀번호|번호|암호|코드|숫자|\\b(password|passcode|pin|code|number)\\b", RegexOption.IGNORE_CASE)
    private val sentenceSplit = Regex("[.?!\\n]+")

    /** [text] 의 값 숫자(날짜·시각 단위가 붙은 숫자 제외), 나온 순서대로 중복 없이. */
    fun valuesIn(text: String): List<String> =
        number.findAll(text)
            .filterNot {
                val after = text.substring(it.range.last + 1)
                val before = text.substring(0, it.range.first)
                dateTimeUnit.containsMatchIn(after) || monthBeforeNumber.containsMatchIn(before)
            }
            .map { it.value }
            .distinct()
            .toList()

    /**
     * 메모들의 핵심 주제 두 글자 묶음 — 숫자와 범용 명사([genericWords])를 뺀 뒤 남은 두 글자 묶음.
     */
    private fun subjectBigrams(notes: List<String>): Set<String> =
        notes.flatMap { note ->
            val specific = note.replace(number, " ").replace(genericWords, " ")
            BigramMatcher.bigrams(specific).ifEmpty { BigramMatcher.bigrams(note.replace(number, " ")) }
        }.toSet()

    /**
     * 요약 한 건에서 [notes] 의 주제와 관련된 문장의 값 숫자만 추린다.
     * - "(과거 대화) 제목: 본문" 또는 "(Past conversation) Title: Body" 형식인 실제 요약에 메모의 핵심 주제가 전혀 안 나오면 다른 주제 요약이므로 빈 목록을 돌려준다.
     * - 여러 문장으로 된 요약에서 일부 문장만 메모의 핵심 주제를 담고 있으면 그 문장들의 숫자만 뽑는다.
     */
    private fun relevantValuesIn(episodeText: String, notes: List<String>): List<String> {
        val subjects = subjectBigrams(notes)
        if (subjects.isEmpty()) return valuesIn(episodeText)

        val epBigrams = BigramMatcher.bigrams(episodeText)
        if (epBigrams.none { it in subjects }) {
            val isEpisodeLine = episodeText.contains(EPISODE_MARKER_KO) || episodeText.contains(EPISODE_MARKER_EN)
            return if (isEpisodeLine) emptyList() else valuesIn(episodeText)
        }

        val body = episodeText.substringAfter(": ", episodeText)
        val sentences = body.split(sentenceSplit).map { it.trim() }.filter { it.isNotEmpty() }
        if (sentences.size <= 1) return valuesIn(episodeText)

        val matchedSentences = sentences.filter { s -> BigramMatcher.bigrams(s).any { it in subjects } }
        return if (matchedSentences.isNotEmpty()) {
            matchedSentences.flatMap(::valuesIn).distinct()
        } else {
            valuesIn(episodeText)
        }
    }

    /**
     * 요약이 메모들과 다른 값을 적었는가 — 숫자가 있는 메모가 있고, 요약의 관련 문장에 그 메모들의 값이 하나도 없으며 다른 값이 있다.
     */
    fun contradicts(episodeText: String, notes: List<String>): Boolean {
        val noteValues = notes.flatMap(::valuesIn).toSet()
        if (noteValues.isEmpty()) return false
        val episodeValues = relevantValuesIn(episodeText, notes)
        return episodeValues.isNotEmpty() && episodeValues.none { it in noteValues }
    }

    /** 줄인 요약들에 적힌 다른 값 — 메모의 값은 빼고, 나온 순서대로 최대 [max]개. */
    fun otherValues(episodeTexts: List<String>, notes: List<String>, max: Int = 5): List<String> {
        val noteValues = notes.flatMap(::valuesIn).toSet()
        return episodeTexts.flatMap { relevantValuesIn(it, notes) }.distinct().filterNot { it in noteValues }.take(max)
    }
}
