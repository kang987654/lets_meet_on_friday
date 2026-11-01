package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.domain.model.ChatMessage

/**
 * 메모 저장을 누가 원했는가 — 사용자 말의 저장 낱말, 바로 앞 비서 말의 저장 물음, 그 물음에 대한 짧은 긍정.
 *
 * [WHY] 저장 낱말 없이 사실만 말해도 모델이 메모 저장을 불렀다 — 대화 기록에 "사실 → 저장 시트 → 거절"이 쌓일수록 심해져(exp62g·exp64 3쌍 5/7)
 * 프롬프트로는 못 막는다(0.38.8 M3 는 새 대화에서만 들었다). 앱이 막고 먼저 묻게 한다(0.38.9 결정 1). 낱말은 넓게 잡는다 — 놓치면 저장 대신
 * 한 번 더 물을 뿐이고, 좁으면 "알아둬"·"까먹으면 안 돼" 같은 정상 부탁이 막힌다(exp64 S 목록 밖 5/6 이 저장 부탁).
 */
object MemoSaveIntent {
    // [WHY] "적어"·"기록"은 부탁하는 꼴만 — "오늘 있었던 일을 길게 적어 볼게"가 저장 부탁으로 잡혀 사실 가드를 지나 저장 시트가 떴다(0.39.2 M4 실기기).
    private val SAVE_WORD_KO = Regex("기억|저장|메모|잊지\\s*마|잊으면\\s*안|까먹|적어\\s*(둬|두|놔|줘|주)|기록\\s*(해|좀)|외워|알아\\s*(둬|두|놔)|챙겨")

    // [WHY] 영어에서는 "Do you remember my password?"(조회 의문문)나 "I wrote a long note"(일상 서술)가 저장 낱말에 걸리지 않도록
    // 명령·부탁 표현만 인정하고 조회 의문문은 제외한다(0.40.0 M2a).
    private val SAVE_WORD_EN = Regex(
        "\\b(please\\s+remember|remember\\s+(that|this|to|my)\\b|save\\s+(this|it|that|my|as\\s+a\\s+note)\\b|" +
            "(make|take|save|keep)\\s+a\\s+note(\\s+(of|that))?\\b|note\\s+(this|that|it)(\\s+down)?\\b|" +
            "write\\s+(it\\s+|this\\s+|that\\s+)?down|jot\\s+(it\\s+|this\\s+|that\\s+)?down|" +
            "don'?t\\s+forget|keep\\s+(this\\s+|that\\s+|it\\s+)?in\\s+mind|memorize\\s+(this|that|it))",
        RegexOption.IGNORE_CASE
    )
    private val REMEMBER_LOOKUP_EN = Regex(
        "^\\s*(do|can|could|did)\\s+you\\s+remember\\b|\\bremember\\s+(what|where|when|who|which|how)\\b",
        RegexOption.IGNORE_CASE
    )

    private val SAVE_ASK = Regex(
        "(메모|저장|기억)[^.?!\\n]{0,8}(둘까|드릴까|할까)|" +
            "(would\\s+you\\s+like(\\s+me)?\\s+to|should\\s+i|shall\\s+i|do\\s+you\\s+want(\\s+me)?\\s+to|want\\s+me\\s+to)\\s+(save|note|remember|keep|write\\s+down)",
        RegexOption.IGNORE_CASE
    )
    private val YES = Regex(
        "(응|웅|어|네|예|넹|ㅇㅇ|그래|그래요|좋아|좋아요|부탁해|부탁해요|해줘|해 줘|해주세요|해 주세요|그럼|오케이|" +
            "yes|yeah|yep|yup|sure|ok|okay|please|go\\s+ahead|yes\\s+please|sure\\s+please|please\\s+do|do\\s+it)" +
            "(\\s*(응|좋아|부탁해|해줘|해 줘|please|thanks|thank\\s+you))?",
        RegexOption.IGNORE_CASE
    )

    // [WHY] 문장 끝에 있을 때만 본다 — "전에 저장했던 번호는 4821이에요"처럼 답 안에 든 낱말까지 지우면 답이 사라진다.
    private val CLAIM_END = Regex(
        "((기억|메모|저장)(해)?\\s?(둘게요|드릴게요|두겠습니다|두었어요|두었습니다|뒀어요|놓을게요|놨어요)|기억할게요|기억하겠습니다|기억했어요|저장했어요|저장했습니다|메모했어요|" +
            "\\b(i'?ll|i\\s+will)\\s+(remember|save|note|keep\\s+a\\s+note\\s+of|keep)\\s+(that|this|it)(\\s+in\\s+mind|\\s+for\\s+you)?|" +
            "\\b(i'?ve|i\\s+have)\\s+(saved|noted|remembered|written\\s+down)\\s+(that|this|it)(\\s+for\\s+you)?)$",
        RegexOption.IGNORE_CASE
    )
    private val SENTENCE_SPLIT = Regex("(?<=[.!?~])\\s+|\\n+")

    /** 모델에 돌려주는 막힘 결과 — exp64b G3(물어봄 6/7, "응" 뒤 저장이 셋 중 가장 많았다). */
    const val ASK_FIRST = "아직 저장하지 않았습니다. 저장하기 전에 \"메모해 둘까요?\"라고 물어보세요."
    const val ASK_FIRST_EN = "Not saved yet. Ask the user \"Would you like me to save this as a note?\" before saving."

    const val ASK = "메모해 둘까요?"
    const val ASK_EN = "Would you like me to save this as a note?"

    fun askFirst(lang: AppLanguage = AppLanguage.KO): String =
        if (lang == AppLanguage.EN) ASK_FIRST_EN else ASK_FIRST

    fun ask(lang: AppLanguage = AppLanguage.KO): String =
        if (lang == AppLanguage.EN) ASK_EN else ASK

    /** 사용자 말에 저장을 부탁하는 낱말이 있는가. */
    fun hasSaveWord(userText: String): Boolean {
        if (SAVE_WORD_KO.containsMatchIn(userText)) return true
        if (REMEMBER_LOOKUP_EN.containsMatchIn(userText)) return false
        if (userText.trimEnd().endsWith("?") && Regex("^\\s*remember\\b", RegexOption.IGNORE_CASE).containsMatchIn(userText)) return false
        return SAVE_WORD_EN.containsMatchIn(userText)
    }

    /** 비서 말이 저장할지 묻는가("메모해 둘까요?", "Would you like me to save this as a note?"). */
    fun asksSave(assistantText: String?): Boolean = assistantText != null && SAVE_ASK.containsMatchIn(assistantText)

    /** 짧은 긍정 한마디인가("응", "그래 부탁해", "yes") — 저장 물음에 대한 답으로만 쓴다. */
    fun isYes(userText: String): Boolean = YES.matches(userText.trim().trimEnd { !it.isLetterOrDigit() })

    /**
     * 모델이 부른 메모 저장을 막을까 — 사용자가 부탁하지도, 비서가 물어 승낙받지도 않았으면 막는다.
     *
     * [WHY] 물음만 보고 통과시키면 "메모해 둘까요?" 뒤의 다른 말("그냥 들어줘서 고마워")에도 저장 시트가 떴다(0.39.2 M4 실기기) — 승낙은 짧은 긍정일 때만.
     * 긴 긍정("그래, 근데 내일 일정도 알려줘")은 막혀 한 번 더 묻게 될 뿐이다.
     */
    fun shouldBlock(userText: String, previousAssistant: String?): Boolean =
        !hasSaveWord(userText) && !(asksSave(previousAssistant) && isYes(userText))

    /**
     * 저장하지 않은 턴의 답에서 "기억해 둘게요"·"저장했어요" 문장을 빼고, 물음이 없으면 [ask]를 붙인다. 고칠 것이 없으면 그대로.
     *
     * [WHY] 툴 없이 하는 약속은 지켜지지 않는다 — "기억해 둘게요. 메모해 둘까요?"처럼 물음과 섞이기도 한다(exp64 F h0 2/7).
     */
    fun withoutClaim(reply: String, lang: AppLanguage = AppLanguage.KO): String {
        val sentences = reply.split(SENTENCE_SPLIT).filter { it.isNotBlank() }
        val kept = sentences.filterNot { CLAIM_END.containsMatchIn(it.trim().trimEnd { c -> !c.isLetterOrDigit() }) }
        if (kept.size == sentences.size) return reply
        // 이모지만 남은 조각("😊")은 빈 답으로 본다.
        val rest = kept.joinToString(" ").trim().takeIf { r -> r.any { it.isLetterOrDigit() } }.orEmpty()
        val askPhrase = ask(lang)
        return when {
            rest.isEmpty() -> askPhrase
            asksSave(rest) -> rest
            else -> "$rest $askPhrase"
        }
    }

    /** 지금 턴 바로 앞의 비서 말과 그 앞 사용자 말 — 지금 턴의 사용자 메시지(문서 블록 포함)는 건너뛴다. */
    data class PreviousTurn(val assistant: String?, val user: String?)

    fun previousTurn(recent: List<ChatMessage>): PreviousTurn {
        val older = recent.sortedByDescending { it.createdAt }.dropWhile { it.role == ChatMessage.Role.USER }
        val assistant = older.firstOrNull()?.takeIf { it.role == ChatMessage.Role.ASSISTANT }
        val user = older.drop(1).firstOrNull { it.role == ChatMessage.Role.USER }
        return PreviousTurn(assistant?.content, user?.content)
    }
}
