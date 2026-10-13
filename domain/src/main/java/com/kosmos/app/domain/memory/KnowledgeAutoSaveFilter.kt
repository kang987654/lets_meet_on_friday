package com.kosmos.app.domain.memory

import com.kosmos.app.domain.cleanup.MemoryMergeGuard
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.search.BigramMatcher

/**
 * 자동 추출한 지식을 **저장하기 직전** 거르는 결정적 규칙 (0.36.0 D2).
 *
 * [WHY] 자동 저장은 사용자 확인 없이 쓰는 경로라(0.24.0 G3 예외), 사용자가 확인한 것을 거스르거나 쓰레기를 쌓는 경우를 규칙으로
 * 막는다 — 실기기(2026-10-12)에서 거절한 메모가 저장되고, 틀린 답의 숫자가 수동 메모와 다른 값으로 저장되고, 끝난 리마인더 요청이 쌓였다.
 * 사용자가 직접 저장하는 `add_memory` 승인 경로에는 적용하지 않는다.
 */
object KnowledgeAutoSaveFilter {

    enum class Skip {
        /** 같은 에피소드에서 사용자가 저장을 거절한 내용과 겹친다. */
        REJECTED,

        /** 기존 메모와 대상이 같은데 숫자가 다르다 — 확인 안 된 값이 확인된 값을 덮지 못하게. */
        CONFLICT,

        /** 처리가 끝난 일회성 리마인더 요청이다. */
        REMINDER_REQUEST,

        /** 사실에 든 숫자를 사용자가 말한 적이 없다 — 비서가 말한 숫자다. */
        ASSISTANT_NUMBER
    }

    /** 거절 내용과의 겹침 하한 — 승인 설명에 붙는 "저장할 내용:" 머리를 감안한 값(KnowledgeAutoSaveFilterTest 판정 표). */
    private const val MIN_REJECTED_OVERLAP = 0.5

    /**
     * 충돌로 볼 대상 겹침 하한(숫자를 뺀 본문).
     *
     * [WHY] 병합 후보 하한([MemoryMergeGuard.MIN_TEXT_OVERLAP])보다 높다 — "비밀번호" 같은 흔한 낱말만 겹치는 "현관 비밀번호 2580" 과
     * "자전거 비밀번호는 134" 가 0.6 으로 충돌이 되면 진짜 새 사실을 버린다. 같은 대상은 1.0 근처다.
     */
    private const val MIN_CONFLICT_SUBJECT_OVERLAP = 0.8

    private val digits = Regex("\\d+")
    private val reminderVerb = Regex("알림|알려|리마인드|리마인더")
    private val clockTime = Regex("\\d+\\s*시|\\d{1,2}:\\d{2}|\\d+\\s*분\\s*(뒤|후)|\\d+\\s*시간\\s*(뒤|후)")
    // [WHY] "늘"은 넣지 않는다 — "오늘"에 걸려 일회성 요청이 반복으로 오판된다.
    private val recurring = Regex("매일|매주|매달|매월|평일|마다|항상")

    /**
     * 저장하지 않을 이유. null 이면 저장한다.
     *
     * @param rejected 같은 에피소드 구간에서 거절된 `add_memory` 승인 설명(감사 기록 원문)
     * @param existing 비교할 기존 메모(수동·자동 모두)
     * @param userText 이 에피소드에서 사용자가 한 말 전부 — null 이면 숫자 출처 검사를 건너뛴다
     */
    fun skipReason(fact: String, rejected: List<String>, existing: List<KnowledgeNote>, userText: String? = null): Skip? = when {
        rejected.any { BigramMatcher.containment(fact, it) >= MIN_REJECTED_OVERLAP } -> Skip.REJECTED
        existing.any { conflicts(fact, it.content) } -> Skip.CONFLICT
        isReminderRequest(fact) -> Skip.REMINDER_REQUEST
        userText != null && numbersOnlyFromAssistant(fact, userText) -> Skip.ASSISTANT_NUMBER
        else -> null
    }

    /**
     * 사실의 숫자 중 사용자 발화에 나온 것이 하나도 없다.
     *
     * [WHY] 추출 지시의 "비서의 발언은 제외" 규칙을 모델이 어긴다 — 비서가 틀리게 답한 "자전거 비밀번호 4321"을 사실로 뽑았고
     * (0.36.0 실기기), 지시를 보강해도 그대로였다(exp49 extract, 두 조건 모두 오추출 1). 숫자는 사용자가 말했는지 글자로 확인할 수 있다.
     */
    fun numbersOnlyFromAssistant(fact: String, userText: String): Boolean {
        val numbers = MemoryMergeGuard.numbersIn(fact)
        return numbers.isNotEmpty() && numbers.none { it in MemoryMergeGuard.numbersIn(userText) }
    }

    /** 둘 다 숫자가 있고, 숫자 집합이 다르며, 숫자를 뺀 본문은 같은 대상으로 볼 만큼 겹친다. */
    fun conflicts(fact: String, note: String): Boolean {
        val a = MemoryMergeGuard.numbersIn(fact)
        val b = MemoryMergeGuard.numbersIn(note)
        if (a.isEmpty() || b.isEmpty() || a == b) return false
        return BigramMatcher.containment(digits.replace(fact, ""), digits.replace(note, "")) >= MIN_CONFLICT_SUBJECT_OVERLAP
    }

    /** "3시 40분에 스트레칭하기 알림 설정 필요" 같은 일회성 요청 — "매일 아침 7시에 운동 알림"처럼 반복이면 습관이라 남긴다. */
    fun isReminderRequest(fact: String): Boolean =
        reminderVerb.containsMatchIn(fact) && clockTime.containsMatchIn(fact) && !recurring.containsMatchIn(fact)

    /**
     * 노트에 붙일 태그 — 후보(에피소드 태그) 중 **노트 내용에 실제로 들어 있는 것만**, 최대 [max]개.
     *
     * [WHY] 에피소드 태그를 그대로 붙이면 한 대화의 여러 사실이 서로의 태그를 나눠 가져 태그 검색이 엉뚱한 노트를 끌어온다(실기기:
     * 녹차 노트에 "자전거 비밀번호" 태그).
     */
    fun tagsFor(fact: String, candidates: List<String>, max: Int): List<String> {
        val compactFact = fact.filter { it.isLetterOrDigit() }
        return candidates.filter { tag ->
            val compactTag = tag.filter { it.isLetterOrDigit() }
            compactTag.isNotEmpty() && compactFact.contains(compactTag)
        }.take(max)
    }
}
