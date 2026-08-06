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
}
