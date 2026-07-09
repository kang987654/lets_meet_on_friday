package com.localfriday.app.assistant.context

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.core.common.Constants
import com.localfriday.app.domain.memory.ConversationRepository
import com.localfriday.app.domain.model.ChatMessage
import com.localfriday.app.domain.tool.Tokenizer
import javax.inject.Inject

/**
 * [ContextBuilder]
 * LLM 모델에게 전달할 현재 세션의 컨텍스트(과거 대화 기록 등)를 구성하는 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Context Management)
 * - **Dependencies**: [ConversationRepository], [Tokenizer]
 *
 * ### Key Flow
 * 1. 세션 ID를 기반으로 최근 대화 기록 조회
 * 2. 모델의 Context Window 한도(예: 3000 토큰)를 넘지 않도록 Token Sliding Window 적용
 * 3. 최적화된 [ChatMessage] 목록을 포함하는 [Context] 반환
 */
class ContextBuilder @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val tokenizer: Tokenizer
) {
    data class Context(
        val recentConversations: List<ChatMessage>,
        val sessionId: String
    )

    suspend fun build(sessionId: String): AppResult<Context> {
        // Fetch up to 50 recent conversations to apply sliding window
        val conversationsResult = conversationRepository.getRecentBySession(
            sessionId,
            50
        )

        // TODO(v1): Fetch knowledge from KnowledgeRepository and include it in Context

        return when (conversationsResult) {
            is AppResult.Success -> {
                val slidingWindow = applyTokenSlidingWindow(conversationsResult.data)
                AppResult.Success(
                    Context(
                        recentConversations = slidingWindow,
                        sessionId = sessionId
                    )
                )
            }
            is AppResult.Failure -> AppResult.Failure(conversationsResult.error)
        }
    }

    private fun applyTokenSlidingWindow(messages: List<ChatMessage>): List<ChatMessage> {
        var currentTokens = 0
        val maxTokens = 3000 // Safe buffer under 4096 tokens
        val selectedMessages = mutableListOf<ChatMessage>()
        
        // messages is chronological (oldest first). We iterate from newest (end) to oldest.
        for (message in messages.reversed()) {
            val msgTokens = tokenizer.sizeInTokens(message.content) + 10 // overhead for tags
            if (currentTokens + msgTokens > maxTokens) {
                break
            }
            currentTokens += msgTokens
            selectedMessages.add(message)
        }
        
        // Restore chronological order
        return selectedMessages.reversed()
    }
}
