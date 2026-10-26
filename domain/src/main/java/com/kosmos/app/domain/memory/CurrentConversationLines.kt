package com.kosmos.app.domain.memory

import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.search.BigramMatcher

/**
 * 기억 검색 결과 맨 위에 실을 "지금 대화"의 사용자 말 — 질의 낱말이 겹치는 최근 문장.
 *
 * [WHY] 방금 "회의실은 5층 C룸이야"라고 해 놓고 "회의실 어디라고 했지?"라고 물으면, 모델은 검색된 옛 지난 대화·메모 값("3층 B룸")을
 * 답했다(실기기 U, exp62 C 0/4). 안내 문구로는 최고 2/4 라(exp62b), 그 문장을 결과 맨 위에 그대로 보이면 4/4(exp62c R1).
 * 우선순위는 방금 대화 > 메모 > 지난 대화(0.38.8 결정 2).
 */
object CurrentConversationLines {

    /** "지금 대화"의 범위 — 에피소드 경계(대화가 이만큼 쉬면 닫힌다)와 같은 값. */
    const val WINDOW_MS = 30 * 60 * 1000L

    const val MAX_LINES = 2

    /** 질의 항 하나라도 이만큼 겹쳐야 싣는다 — 전수 스캔 통과선과 같다. */
    const val MIN_TERM_OVERLAP = 0.5

    /**
     * @param messages 같은 세션의 최근 메시지(순서 무관).
     * @param terms 검색어 낱말.
     * @return 겹친 사용자 문장(최신 먼저, 최대 [MAX_LINES]) — 가장 최근 사용자 말(지금 던진 질문)은 뺀다.
     */
    fun pick(messages: List<ChatMessage>, terms: List<String>, now: Long): List<String> {
        val userTurns = messages
            .filter { it.role == ChatMessage.Role.USER && now - it.createdAt in 0..WINDOW_MS }
            .sortedByDescending { it.createdAt }
            .drop(1)
        return userTurns
            .filter { it.content.isNotBlank() && BigramMatcher.bestTermOverlap(terms, it.content) >= MIN_TERM_OVERLAP }
            .take(MAX_LINES)
            .map { it.content.replace('\n', ' ').trim() }
    }
}
