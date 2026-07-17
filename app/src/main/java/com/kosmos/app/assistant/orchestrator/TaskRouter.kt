package com.kosmos.app.assistant.orchestrator

import com.kosmos.app.assistant.agent.BaseAgent
import com.kosmos.app.assistant.agent.CalendarAgent
import com.kosmos.app.assistant.agent.DefaultAgent
import javax.inject.Inject

/**
 * [TaskRouter]
 * 사용자의 의도를 분석하여 적절한 에이전트로 작업을 라우팅하는 책임을 가집니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant Orchestrator
 * - **Dependencies**: [IntentClassifier], [CalendarAgent], [DefaultAgent]
 *
 * ### Key Flow
 * 1. [IntentClassifier]를 통해 입력 텍스트의 의도를 분석합니다.
 * 2. 분석된 의도에 따라 특화된 [BaseAgent] 구현체를 반환합니다.
 */
class TaskRouter @Inject constructor(
    private val intentClassifier: IntentClassifier,
    private val calendarAgent: CalendarAgent,
    private val defaultAgent: DefaultAgent
) {
    fun route(userInput: String): BaseAgent {
        val intent = intentClassifier.classify(userInput)
        return when (intent) {
            Intent.CALENDAR_WRITE, Intent.CALENDAR_READ -> calendarAgent
            else -> defaultAgent
        }
    }
}
