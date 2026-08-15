package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.model.ChatMessage

/**
 * [v0] 대화 내역 저장소
 * 시간 변환이나 Enum 매핑과 같은 mapper 책임은 구현체(Impl)에서 일관되게 처리해야 합니다.
 */
interface ConversationRepository {
    suspend fun save(message: ChatMessage): AppResult<Unit>
    /**
     * [WHY] `limit` 의 기본값(당시 `MAX_CONVERSATION_TURNS = 5`)을 **제거했다.** 기본값이 있으면
     * 인자를 잊은 호출자가 조용히 5개만 받는데, 실제로 `ChatViewModel.loadMessages` 가 그렇게
     * 호출해 **채팅 화면을 열면 마지막 5개 메시지만 보였다.** 필수 인자로 만들어 그 실수를
     * 컴파일 단계에서 막는다.
     */
    suspend fun getRecentBySession(
        sessionId: String,
        limit: Int
    ): AppResult<List<ChatMessage>>
    suspend fun getPagedBySession(sessionId: String, offset: Int, limit: Int): AppResult<List<ChatMessage>>

    /** 에피소드에 귀속된 메시지 전부 — 시간순 (요약 입력·원문 열람용, ADR-022). */
    suspend fun getByEpisode(episodeId: String): AppResult<List<ChatMessage>>

    /** 에피소드 미배정 메시지 — catch-up 소급 배정 대상. 시간순. */
    suspend fun getUnassigned(): AppResult<List<ChatMessage>>

    /** 메시지의 에피소드 귀속을 갱신합니다 (소급 배정용). */
    suspend fun assignEpisode(messageId: String, episodeId: String): AppResult<Unit>

    /**
     * 세션 무관 연속 타임라인 페이징 — [beforeTs] 이전 행만, 최신순(DESC) 그대로 반환.
     * [WHY] 앵커 이전 집합은 불변이라 offset 페이징이 안전하다 (시안 A′ 타임라인).
     */
    suspend fun getPagedAll(beforeTs: Long, offset: Int, limit: Int): AppResult<List<ChatMessage>>

    /** [ts] 이후(포함) 메시지 수 — 타임라인 점프 인덱스 계산용. */
    suspend fun countNewerThan(ts: Long): AppResult<Int>

    /** 앵커 이전 메시지 총수 — Paging placeholder 의 전체 크기. */
    suspend fun countOlderThan(beforeTs: Long): AppResult<Int>
}
