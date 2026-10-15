package com.kosmos.app.domain.memory

/**
 * 기억 검색 결과에서 **직접 저장한 메모와 다른 값을 적은 과거 대화 요약**을 가려냅니다 (0.37.2).
 *
 * [WHY] 요약이 여러 건 다른 값을 적으면 "메모를 믿으라"는 안내가 있어도 모델이 많은 쪽을 따른다(exp52: 4321 요약 3건이면 깊이와 무관하게
 * 메모 134 를 버림). 그런 요약을 "다른 값이 적혀 있다" 한 줄로 줄이면 메모를 답하고 다른 값도 덧붙인다(exp53 조건 C2: 충돌 5/8 → 8/8, 대조 4/4).
 *
 * [WHY] 주제가 같은지는 따로 재지 않는다 — 같은 검색어로 함께 걸린 것이 그 근거다. 문자열 겹침으로 재면 "회사 사물함 번호는 58번"처럼
 * 짧은 메모는 같은 주제의 요약과도 겹침이 낮아 놓쳤다. 날짜·시각 숫자("10월 3일")는 값이 아니라 빼고 본다.
 */
object RecallConflict {

    private val number = Regex("\\d+")

    // [WHY] 숫자 뒤에 날짜·시각·기간 단위가 붙으면 값이 아니다 — "10월 3일 자물쇠 교체" 같은 요약을 충돌로 잘못 줄이지 않게.
    private val dateTimeUnit = Regex("^\\s*(년|월|일|시|분|초|요일|주|개월|시간)")

    /** [text] 의 값 숫자(날짜·시각 단위가 붙은 숫자 제외), 나온 순서대로 중복 없이. */
    fun valuesIn(text: String): List<String> =
        number.findAll(text)
            .filterNot { dateTimeUnit.containsMatchIn(text.substring(it.range.last + 1)) }
            .map { it.value }
            .distinct()
            .toList()

    /**
     * 요약이 메모들과 다른 값을 적었는가 — 숫자가 있는 메모가 있고, 요약에 그 메모들의 값이 하나도 없으며, 요약에 다른 값이 있다.
     */
    fun contradicts(episodeText: String, notes: List<String>): Boolean {
        val noteValues = notes.flatMap(::valuesIn).toSet()
        if (noteValues.isEmpty()) return false
        val episodeValues = valuesIn(episodeText)
        return episodeValues.isNotEmpty() && episodeValues.none { it in noteValues }
    }

    /** 줄인 요약들에 적힌 다른 값 — 메모의 값은 빼고, 나온 순서대로 최대 [max]개. */
    fun otherValues(episodeTexts: List<String>, notes: List<String>, max: Int = 5): List<String> {
        val noteValues = notes.flatMap(::valuesIn).toSet()
        return episodeTexts.flatMap(::valuesIn).distinct().filterNot { it in noteValues }.take(max)
    }
}
