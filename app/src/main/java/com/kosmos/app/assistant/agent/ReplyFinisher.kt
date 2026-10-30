package com.kosmos.app.assistant.agent

import com.kosmos.app.domain.agent.RefusalTurns
import com.kosmos.app.domain.memory.MemoSaveIntent

/**
 * [ReplyFinisher]
 * 툴 루프가 끝난 뒤 모델 답을 화면·저장 전에 고칠지, 다음 턴에 대화를 다시 만들지 정하는 판정입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant Agent — [BaseAgent] 가 루프 뒤에 부른다. 순수 판정이고, 앱이 여는 저장(승인 시트)의 실행은 [BaseAgent] 몫이다.
 *
 * ### Key Flow
 * 1. [finish] — 툴을 하나도 실행하지 않은 턴만: 저장 물음에 "응"이면 앱이 여는 저장, 아니면 지켜지지 않을 약속 문장을 물음으로.
 * 2. [needsRebuild] — 살아 있는 대화(KV)와 다음 턴의 모델 입력 기록이 갈리는 답이면 다음 사용자 턴에 다시 만든다.
 * 장치 목록과 각각의 근거는 ADR-030.
 */
internal object ReplyFinisher {

    /** 루프 뒤 답을 어떻게 끝낼지. */
    sealed interface Finish {
        /** 그대로. */
        data object Keep : Finish

        /** 앱이 메모 저장을 직접 연다 — 실행 경로에 들지 못하면 [fallback] 답. */
        data class AppSave(val memo: String, val fallback: String) : Finish

        /** 답을 이 문장으로 바꾼다. */
        data class Rewrite(val text: String) : Finish
    }

    /**
     * [WHY] 툴을 하나도 실행하지 않은 턴만 고친다 — 저장 물음에 "응"이라 했는데 모델이 저장을 부르지 않고 "메모해 두었어요"라고만 하면
     * 앱이 저장 시트를 직접 연다(exp64b 2/6, 0.38.9 사용자 결정). 그 밖에는 지켜지지 않을 "기억해 둘게요"를 물음으로 바꾼다.
     *
     * @param carriedMemo 앞 턴에 사실 저장 가드가 막은 메모(모델이 쓴 문장) — 있으면 앞 사용자 말보다 먼저 쓴다.
     */
    suspend fun finish(
        reply: String,
        userText: String,
        // 앞 턴은 DB 를 읽어야 해서 필요할 때만 부른다(저장 물음에 대한 "응"일 때).
        previous: suspend () -> MemoSaveIntent.PreviousTurn,
        carriedMemo: String?,
        anyExecuted: Boolean,
        memoCalled: Boolean,
        memoAllowed: Boolean
    ): Finish {
        if (anyExecuted || memoCalled) return Finish.Keep
        val askedBefore = MemoSaveIntent.isYes(userText) && MemoSaveIntent.asksSave(previous().assistant)
        val memo = if (askedBefore) carriedMemo ?: previous().user else null
        val withoutClaim = MemoSaveIntent.withoutClaim(reply)
        return when {
            memo != null && memoAllowed -> Finish.AppSave(memo, withoutClaim)
            withoutClaim != reply -> Finish.Rewrite(withoutClaim)
            else -> Finish.Keep
        }
    }

    /**
     * [WHY] 거절 답은 다음 턴의 모델 입력 기록에서 빠진다(ContextBuilder) — 살아 있는 대화(KV)에는 남아 있으므로 다시 만든다(exp64c).
     * 거절을 되물은 턴은 거절을 담아 만든 대화라 이 턴 뒤에 다시 빼고 만든다(exp64d).
     */
    fun needsRebuild(finalText: String, refusalAsked: Boolean): Boolean = RefusalTurns.isRefusal(finalText) || refusalAsked
}
