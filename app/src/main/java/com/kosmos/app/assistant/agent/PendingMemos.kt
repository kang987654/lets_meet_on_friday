package com.kosmos.app.assistant.agent

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사실 저장 가드가 막은 메모 — 대화방마다 하나, 다음 턴이 꺼내 간다.
 *
 * [WHY] 다음 턴이 저장 물음에 "응"으로 답하면 모델이 쓴 이 문장을 승인 시트에 그대로 쓴다(0.38.9 M1). 에이전트 필드에 두면 에이전트 인스턴스
 * (화면의 ViewModel 단위)와 수명이 묶이고, 다른 대화방의 턴이 지웠다 — 앱 전체에서 하나를 대화방 단위로 둔다(0.39.2 M1).
 */
@Singleton
class PendingMemos @Inject constructor() {
    private val memos = ConcurrentHashMap<String, String>()

    fun put(sessionId: String, memo: String) {
        memos[sessionId] = memo
    }

    /** 이 대화방의 막아 둔 메모를 꺼내고 지운다. */
    fun take(sessionId: String): String? = memos.remove(sessionId)
}
