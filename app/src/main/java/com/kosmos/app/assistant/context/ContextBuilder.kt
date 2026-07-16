package com.kosmos.app.assistant.context

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.data.local.prefs.SettingsDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * [ContextBuilder]
 * LLM 모델에게 전달할 현재 세션의 컨텍스트(과거 대화 기록 등)를 구성하는 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Context Management)
 * - **Dependencies**: [ConversationRepository], [Tokenizer], [SettingsDataStore]
 *
 * ### Key Flow
 * 1. 세션 ID를 기반으로 최근 대화 기록 조회
 * 2. 모델의 Context Window 한도(예: 3000 토큰)를 넘지 않도록 Token Sliding Window 적용
 * 3. 최적화된 [ChatMessage] 목록을 포함하는 [Context] 반환
 */
class ContextBuilder @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val tokenizer: Tokenizer,
    private val settingsDataStore: SettingsDataStore
) {
    data class Context(
        val recentConversations: List<ChatMessage>,
        val sessionId: String,
        val responseStyle: String
    )

    suspend fun build(sessionId: String): AppResult<Context> {
        // Fetch up to 150 recent conversations to apply sliding window
        val conversationsResult = conversationRepository.getRecentBySession(
            sessionId,
            150
        )

        // TODO(v1): Fetch knowledge from KnowledgeRepository and include it in Context

        val responseStyle = try {
            settingsDataStore.responseStyleFlow.first()
        } catch (e: Exception) {
            "DEFAULT"
        }

        return when (conversationsResult) {
            is AppResult.Success -> {
                val slidingWindow = applyTokenSlidingWindow(conversationsResult.data)
                AppResult.Success(
                    Context(
                        recentConversations = slidingWindow,
                        sessionId = sessionId,
                        responseStyle = responseStyle
                    )
                )
            }
            is AppResult.Failure -> AppResult.Failure(conversationsResult.error)
        }
    }

    private fun applyTokenSlidingWindow(messages: List<ChatMessage>): List<ChatMessage> {
        var currentTokens = 0
        val maxTokens = 8000 // Safe buffer for larger context window
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
