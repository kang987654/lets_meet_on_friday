package com.kosmos.app.domain.agent

import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.domain.memory.RecallConflict
import com.kosmos.app.domain.model.ChatMessage

/**
 * [IdleGapHistory]
 * 오래 쉬었다 돌아온 대화에서 지난 대화를 원문 대신 요약 한 줄로 싣기 위한 판정입니다 (0.41.7 M4).
 *
 * ### Key Flow
 * 1. [split] — 최근 메시지(시간순)에서 **가장 마지막 긴 간격**을 찾아 그 앞(지난 대화)과 뒤(지금 대화)로 나눈다.
 *    마지막 메시지 뒤로 이미 긴 간격이 지났으면(돌아와서 아직 말하기 전 — 대화 미리 준비) 전부가 지난 대화다.
 * 2. 앱([com.kosmos.app.assistant.context.ContextBuilder])이 지난 대화의 마지막 에피소드 요약으로 [line] 을 만들어 시스템 지시 끝에 붙이고,
 *    모델 기록에는 지금 대화만 싣는다. 요약이 없으면 지금처럼 원문을 싣는다.
 *
 * [WHY] 휴대폰(2026-11-06)에서 69분 뒤 "아아 테스트"에 모델이 1시간 전 물음("메모해 둘까요?")을 이어받아 기억 검색을 다시 불렀다.
 * 원문이면 걸린 물음이 직전 턴처럼 보인다 — 실험실 exp81: 간격 뒤 새 말 원문 2/5 · 시스템 지시 끝 요약 줄 5/5 · 지난 대화 첫 model 메시지 4/5,
 * 이어 말하기 세 조건 모두 4/4. 시각 숫자("1시간 지남")는 넣지 않는다(사용자 결정 — 애매하다).
 */
object IdleGapHistory {

    data class Split(val previous: List<ChatMessage>, val current: List<ChatMessage>)

    /**
     * @param messages 시간순(오래된 것 먼저) 최근 메시지
     * @param now 지금 — 판정을 벽시계에 묶지 않는다(AGENTS §2-④)
     * @param gapMs 이만큼 넘게 벌어지면 다른 대화로 본다(에피소드 경계와 같은 값)
     */
    fun split(messages: List<ChatMessage>, now: Long, gapMs: Long): Split {
        if (messages.isEmpty()) return Split(emptyList(), emptyList())
        if (now - messages.last().createdAt > gapMs) return Split(messages, emptyList())
        for (i in messages.size - 1 downTo 1) {
            if (messages[i].createdAt - messages[i - 1].createdAt > gapMs) {
                return Split(messages.subList(0, i), messages.subList(i, messages.size))
            }
        }
        return Split(emptyList(), messages)
    }

    /**
     * 시스템 지시 끝에 붙일 한 줄 — 기억 검색 결과와 같은 꼬리표(`(과거 대화) 제목: 요약`, exp53 C2 — 4B 가 익숙한 표기).
     */
    fun line(title: String?, summary: String, lang: AppLanguage = AppLanguage.KO): String {
        val marker = if (lang == AppLanguage.EN) RecallConflict.EPISODE_MARKER_EN else RecallConflict.EPISODE_MARKER_KO
        val body = summary.replace('\n', ' ').trim()
        return if (title.isNullOrBlank()) "$marker $body" else "$marker ${title.trim()}: $body"
    }
}
