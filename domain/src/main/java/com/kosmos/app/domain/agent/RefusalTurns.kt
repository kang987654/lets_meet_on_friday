package com.kosmos.app.domain.agent

import com.kosmos.app.domain.model.ChatMessage

/**
 * 비서가 "그런 기능은 없어요"라고 거절한 턴 — 모델 입력 기록에서 뺀다(화면·DB 에는 그대로).
 *
 * [WHY] 기록에 거절 답이 있으면 모델이 일상 요청까지 같은 말로 거절했다 — 프롬프트 후보로는 못 고쳤고(0.38.8 M1, exp62d),
 * 거절 쌍(사용자 말 + 거절 답)을 기록에서 빼면 6/12 → 1/12(exp64c). "직접 해 드릴 수는 없지만 …"처럼 이어서 도운 답은 거절로 보지 않는다(0.39.2 M2).
 */
object RefusalTurns {
    // [WHY] 바로 뒤에 "~지만/~는데"가 붙으면 도운 답이다("진단은 해 드릴 수 없지만, 물을 충분히 …") — 빼면 다음 턴이 그 내용을 몰랐고(회귀 세트 overlap 2/3),
    // 남겨도 거절 따라 하기는 거의 늘지 않았다(0.39.2 M2: 24건 중 뺌 4 · 남김 5).
    private val REFUSAL = Regex("(기능이 없|기능은 없|기능을 가지고 있지 않|드릴 수 없|드릴 수는 없|도움을 드리기 어려)(?!지만|는데|으나|운데|우나|워도)")

    // [WHY] 거절을 빼면 "왜 안 된다고 했어?"에 12/12 "어떤 말씀이신지 기억이 안 나요"였다 — 되묻는 턴에만 남기면 12/12 거절한 주제와 이유를 답한다(exp64d).
    // 맨 "왜 안/못"은 앞 대화를 가리킬 때만 — "와이파이가 왜 안 돼?" 같은 일상 말이 걸려 거절을 다시 싣고 대화를 두 번 다시 만들었다(0.39.2 M2).
    private val WHY_QUOTE = Regex("안\\s*된다고|못\\s*한다고|안\\s*된다며|못\\s*한다며")
    private val WHY_BARE = Regex("왜\\s*(안|못)")
    private val WHY_BACK = Regex("그게|그건|그거|그걸|아까|방금|했어|했잖|했지|그랬")

    /** 비서 답이 할 수 없다는 거절인가. */
    fun isRefusal(assistantText: String): Boolean = REFUSAL.containsMatchIn(assistantText)

    /** 사용자 말이 앞의 거절을 되묻는가("왜 안 된다고 했어?", "그게 왜 안 돼?"). */
    fun asksWhyRefused(userText: String): Boolean =
        WHY_QUOTE.containsMatchIn(userText) || (WHY_BARE.containsMatchIn(userText) && WHY_BACK.containsMatchIn(userText))

    /** 모델 입력 기록 — 가장 최근 사용자 말이 거절을 되묻으면 그대로, 아니면 거절 턴을 뺀다. */
    fun forModel(messages: List<ChatMessage>): List<ChatMessage> {
        val lastUser = messages.lastOrNull { it.role == ChatMessage.Role.USER }
        return if (lastUser != null && asksWhyRefused(lastUser.content)) messages else dropFrom(messages)
    }

    /** 시간순 기록에서 거절 답과 바로 앞 사용자 말을 뺀다. */
    fun dropFrom(messages: List<ChatMessage>): List<ChatMessage> {
        val drop = mutableSetOf<Int>()
        messages.forEachIndexed { i, m ->
            if (m.role == ChatMessage.Role.ASSISTANT && isRefusal(m.content)) {
                drop += i
                if (i > 0 && messages[i - 1].role == ChatMessage.Role.USER) drop += i - 1
            }
        }
        return if (drop.isEmpty()) messages else messages.filterIndexed { i, _ -> i !in drop }
    }
}
