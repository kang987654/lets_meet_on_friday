package com.kosmos.app.feature.chat.result

import com.kosmos.app.R
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.MessageResult
import org.json.JSONObject

/**
 * 마지막 비서 답 아래의 상황 칩(0.38.2 M6) — 누르면 [action] 을 한다.
 */
data class ChatChip(val label: String, val action: Action, @param:androidx.annotation.DrawableRes val icon: Int? = null) {
    sealed interface Action {
        /** 그 글자로 한 턴을 보낸다(답 칩). */
        data class Send(val text: String) : Action
        /** 메모와 다른 값을 적은 과거 대화를 연다 — 에피소드 시트에서 고치거나 지운다. */
        data class OpenEpisode(val episodeId: String) : Action
        /** 결과 행을 누른 것과 같다 — 메모 화면·일정 화면. */
        data object OpenResult : Action
    }
}

/**
 * 칩 규칙 — **코드 규칙만, 추론 없음**(계획 0.38.2 "칩"). 규칙에 안 걸리면 칩 없음.
 *
 * - 답 칩: 마지막 문장이 "A…, B…?" / "A… 아니면 B…?" 양자택일이면 두 칩. 두 선택지의 같은 꼬리(“쪽이 좋으세요”)를 떼어 다른 부분만 보인다.
 * - 행동 칩: 기억 답에 충돌이 있으면 [과거 기록 정리], 메모·기억 결과면 [메모 보기], 일정 결과면 [캘린더에서 보기].
 */
object ChatChipRules {

    private const val MAX_OPTION = 16
    private val SENTENCE_END = Regex("(?<=[.!?。])\\s+")
    private val OR_SPLIT = Regex(",\\s*(?:아니면\\s+)?|\\s+아니면\\s+")
    private val PARTICLE = Regex("(이|가|을|를|은|는)$")

    fun of(message: ChatMessage): List<ChatChip> {
        if (message.role != ChatMessage.Role.ASSISTANT) return emptyList()
        return answerChips(message.content) + actionChips(message.result)
    }

    /** 양자택일 질문의 두 선택지. 아니면 빈 목록. */
    fun answerChips(text: String): List<ChatChip> {
        val last = text.trim().split(SENTENCE_END).lastOrNull()?.trim() ?: return emptyList()
        if (!last.endsWith("?")) return emptyList()
        val parts = last.removeSuffix("?").split(OR_SPLIT).map { it.trim() }
        if (parts.size != 2 || parts.any { it.isEmpty() }) return emptyList()
        val (a, b) = parts.map { it.split(Regex("\\s+")) }
        val common = a.reversed().zip(b.reversed()).takeWhile { (x, y) -> x == y }.size
        // [WHY] 같은 꼬리가 없으면("커피로 할까요, 차는 어때요") 선택지 경계를 믿을 수 없다 — 칩을 내지 않는다.
        if (common == 0 || common >= minOf(a.size, b.size)) return emptyList()
        val keep = common - 1 // 꼬리의 마지막 낱말(서술어)만 떼고 앞쪽 공통어는 남긴다: "진한 쪽이" → "진한 쪽"
        val options = listOf(a, b).map { words ->
            words.dropLast(common).plus(words.takeLast(common).take(keep)).joinToString(" ").replace(PARTICLE, "")
        }
        if (options.any { it.isBlank() || it.length > MAX_OPTION } || options[0] == options[1]) return emptyList()
        return options.map { ChatChip(it, ChatChip.Action.Send(it)) }
    }

    fun actionChips(result: MessageResult?): List<ChatChip> = when (result?.kind) {
        MessageResult.Kind.MEMORY_RECALL -> listOfNotNull(conflictChip(result.meta), ChatChip("메모 보기", ChatChip.Action.OpenResult, R.drawable.ic_ph_note))
        MessageResult.Kind.MEMORY_NOTE -> listOf(ChatChip("메모 보기", ChatChip.Action.OpenResult, R.drawable.ic_ph_note))
        MessageResult.Kind.SCHEDULE -> listOf(ChatChip("캘린더에서 보기", ChatChip.Action.OpenResult, R.drawable.ic_ph_calendar_blank))
        MessageResult.Kind.REMINDER, null -> emptyList()
    }

    private fun conflictChip(meta: String?): ChatChip? {
        val json = meta?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val ids = json.optJSONArray("conflictEpisodeIds") ?: return null
        val first = ids.optString(0).takeIf { it.isNotEmpty() } ?: return null
        val count = json.optInt("conflictCount", ids.length())
        return ChatChip(if (count > 1) "과거 기록 ${count}건 정리" else "과거 기록 정리", ChatChip.Action.OpenEpisode(first), R.drawable.ic_ph_chat_text)
    }
}
