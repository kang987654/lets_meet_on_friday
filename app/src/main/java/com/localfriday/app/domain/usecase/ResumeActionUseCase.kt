package com.localfriday.app.domain.usecase

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.agent.AgentResult
import com.localfriday.app.assistant.orchestrator.AssistantOrchestrator
import com.localfriday.app.domain.model.ModelOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ResumeActionUseCase @Inject constructor(
    private val assistantOrchestrator: AssistantOrchestrator
) {
    suspend operator fun invoke(sessionId: String, action: ModelOutput): AppResult<AgentResult> = withContext(Dispatchers.IO) {
        try {
            val result = assistantOrchestrator.resumeAction(sessionId, action)
            AppResult.Success(result)
        } catch (e: Exception) {
            e.printStackTrace()
            AppResult.Failure(com.localfriday.app.core.common.AppError.SearchError(e.message ?: "Action resume failed"))
        }
    }
}
