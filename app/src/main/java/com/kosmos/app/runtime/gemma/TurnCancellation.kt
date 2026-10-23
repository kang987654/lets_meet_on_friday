package com.kosmos.app.runtime.gemma

/**
 * 턴 취소 상태 — 지금 send 중인 대화, 사용자 취소가 닿은 대화, 턴 사이에 들어온 중지 요청을 한곳에서 관리합니다.
 *
 * [WHY] 생성 도중 `cancelProcess` 를 맞은 네이티브 대화는 그 뒤 모든 send 가 즉시 "Task cancelled" 로 끝난다(exp57 A) —
 * 어느 대화가 취소됐는지 알아야 캐시에서 버릴 수 있다. 쉬고 있는 대화에 거는 취소는 해도 없고 효과도 없다(exp57 B) —
 * 그래서 턴 사이 빈 구간(전사 직후·프롬프트 조립·뮤텍스 대기)의 중지는 "다음 채팅 턴을 시작하지 않음"으로 남긴다.
 *
 * 취소는 메인 스레드에서, 시작·끝은 LLM 스레드에서 불리므로 모든 상태 변경을 이 객체로 직렬화한다.
 * 대화 타입을 제네릭으로 둔 것은 네이티브 `Conversation` 없이 JVM 에서 검증하기 위해서다.
 */
internal class TurnCancellation<C : Any>(private val cancelProcess: (C) -> Unit) {
    private var active: C? = null
    private var cancelled: C? = null
    private var pending = false

    /** send 직전에 부른다. */
    @Synchronized
    fun begin(conversation: C) {
        active = conversation
    }

    /** send 가 끝나면(성공·실패 모두) 부른다. @return 이 턴이 사용자 취소를 맞았는지 */
    @Synchronized
    fun end(conversation: C): Boolean {
        val wasCancelled = cancelled === conversation
        active = null
        cancelled = null
        return wasCancelled
    }

    /** 사용자 중지. 진행 중인 대화가 있으면 그 생성을 끊고, 없으면 다음 채팅 턴에 걸어 둔다. */
    fun requestCancel() {
        val target = synchronized(this) {
            val current = active
            if (current != null) cancelled = current else pending = true
            current
        }
        target?.let { runCatching { cancelProcess(it) } }
    }

    /** 걸어 둔 중지가 있으면 소비한다 — 채팅 턴 시작 시. */
    @Synchronized
    fun consumePending(): Boolean = pending.also { pending = false }

    /** 새 요청이 시작되면 지난 요청의 늦은 중지를 지운다. */
    @Synchronized
    fun clearPending() {
        pending = false
    }

    /** 지금 send 중인 대화(엔진 해제 시 먼저 끊기 위해). */
    @Synchronized
    fun activeConversation(): C? = active
}
