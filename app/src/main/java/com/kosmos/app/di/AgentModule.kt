package com.kosmos.app.di

import com.kosmos.app.assistant.orchestrator.AssistantOrchestrator
import com.kosmos.app.assistant.tool.AddMemoryToolExecutor
import com.kosmos.app.assistant.tool.AddReminderToolExecutor
import com.kosmos.app.assistant.tool.AddScheduleToolExecutor
import com.kosmos.app.assistant.tool.GetScheduleToolExecutor
import com.kosmos.app.assistant.tool.SearchMemoryToolExecutor
import com.kosmos.app.assistant.tool.SearchWikipediaToolExecutor
import com.kosmos.app.assistant.tool.ToolExecutor
import com.kosmos.app.domain.agent.AssistantGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import com.kosmos.app.assistant.tool.ListRemindersToolExecutor
import com.kosmos.app.assistant.tool.SearchDocumentsToolExecutor

/**
 * 툴 실행기 멀티바인딩 — [com.kosmos.app.assistant.tool.ToolRegistry] 가 `Set<ToolExecutor>` 로 받는다.
 *
 * [WHY] 예전에는 ToolRegistry 생성자가 실행기 6개를 하나씩 받아 `name to executor` 맵을 손으로
 * 적었다 — 툴을 추가할 때 고칠 곳이 생성자·맵 두 군데였다. 이제 새 툴은 여기 한 줄이다.
 * (선언 쪽 KosmosToolDeclarations 와 활성 목록 KosmosAgent 는 프롬프트 표면이라 별도로 고친다.)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {
    // [WHY] SendChatMessageUseCase(:domain) 가 AssistantOrchestrator(:app) 를 AssistantGateway 로 주입받는다 (ADR-034).
    @Binds abstract fun assistantGateway(o: AssistantOrchestrator): AssistantGateway

    @Binds @IntoSet abstract fun addSchedule(executor: AddScheduleToolExecutor): ToolExecutor
    @Binds @IntoSet abstract fun getSchedule(executor: GetScheduleToolExecutor): ToolExecutor
    @Binds @IntoSet abstract fun addMemory(executor: AddMemoryToolExecutor): ToolExecutor
    @Binds @IntoSet abstract fun searchMemory(executor: SearchMemoryToolExecutor): ToolExecutor
    @Binds @IntoSet abstract fun searchWikipedia(executor: SearchWikipediaToolExecutor): ToolExecutor
    @Binds @IntoSet abstract fun addReminder(executor: AddReminderToolExecutor): ToolExecutor
    @Binds @IntoSet abstract fun listReminders(executor: ListRemindersToolExecutor): ToolExecutor
    @Binds @IntoSet abstract fun searchDocuments(executor: SearchDocumentsToolExecutor): ToolExecutor
}
