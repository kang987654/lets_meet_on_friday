package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.assistant.orchestrator.AssistantOrchestrator
import com.kosmos.app.domain.model.ModelOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ResumeActionUseCase @Inject constructor(
    private val assistantOrchestrator: AssistantOrchestrator
) {
    suspend operator fun invoke(
        sessionId: String, 
        action: ModelOutput,
        onToken: ((String) -> Unit)? = null
    ): AppResult<AgentResult> = withContext(Dispatchers.IO) {
        try {
            val result = assistantOrchestrator.resumeAction(sessionId, action)
            AppResult.Success(result)
        } catch (e: Exception) {
            e.printStackTrace()
            AppResult.Failure(com.kosmos.app.core.common.AppError.SearchError(e.message ?: "Action resume failed"))
        }
    }
}
