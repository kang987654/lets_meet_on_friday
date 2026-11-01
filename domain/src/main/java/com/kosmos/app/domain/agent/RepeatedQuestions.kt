package com.kosmos.app.domain.agent

import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.search.BigramMatcher

/**
 * 사용자가 앞에서 한 질문을 다시 물으면, 그 앞 질문과 비서의 답을 모델 입력 기록에서 뺀다(화면·DB 에는 그대로).
 *
 * [WHY] 모델은 같은 질문에 대한 자기 앞 답을 되풀이했다 — 방금 새 값을 말해도, 검색 결과가 그 값을 맨 위에 보여도 앞에서 틀리게 한 답("134예요")을
 * 따랐다(실기기 0.39.3 M4c, exp69b 2/2). 검색 결과 문구로는 못 고쳤고(exp69c 네 판 모두 2/6), 앞 답을 빼면 6/6.
 */
object RepeatedQuestions {
    private val QUESTION = Regex("뭐|어디|몇|언제|누구|무슨|어떤|기억나|\\?")

    /** 두 질문을 같은 질문으로 보는 글자 묶음 겹침(containment) 하한 — 진술("… 4821이야 기억해줘")은 질문이 아니라 애초에 걸리지 않는다. */
    const val MIN_CONTAINMENT = 0.8

    fun isQuestion(text: String): Boolean = QUESTION.containsMatchIn(text)

    fun same(a: String, b: String): Boolean =
        isQuestion(a) && isQuestion(b) && BigramMatcher.containment(a, b) >= MIN_CONTAINMENT

    /** 시간순 기록 — 가장 최근 사용자 말(지금 질문)과 같은 앞 질문과 그 바로 뒤 비서 답을 뺀다. 뺄 것이 없으면 그대로. */
    fun forModel(messages: List<ChatMessage>): List<ChatMessage> {
        val currentAt = messages.indexOfLast { it.role == ChatMessage.Role.USER }
        if (currentAt < 0) return messages
        val current = messages[currentAt].content
        val drop = mutableSetOf<Int>()
        for (i in 0 until currentAt) {
            val m = messages[i]
            if (m.role == ChatMessage.Role.USER && same(m.content, current)) {
                drop += i
                if (i + 1 < currentAt && messages[i + 1].role == ChatMessage.Role.ASSISTANT) drop += i + 1
            }
        }
        return if (drop.isEmpty()) messages else messages.filterIndexed { i, _ -> i !in drop }
    }
}
