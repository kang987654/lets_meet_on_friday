package com.localfriday.app.domain.usecase

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.agent.AgentResult
import javax.inject.Inject

class ProcessVoiceInputUseCase @Inject constructor(
    private val sendChatMessageUseCase: SendChatMessageUseCase
) {
    suspend operator fun invoke(
        sessionId: String, 
        transcript: String
    ): AppResult<AgentResult> {
        // V1: 확장 가능성 (음성 특유의 메타데이터나 InputType.VOICE 등을 다룰 수 있음)
        return sendChatMessageUseCase(sessionId = sessionId, message = transcript)
    }
}
