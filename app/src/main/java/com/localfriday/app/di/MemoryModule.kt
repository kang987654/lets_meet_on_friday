package com.localfriday.app.di

import com.localfriday.app.data.local.repository.KnowledgeRepositoryImpl
import com.localfriday.app.data.local.repository.TaskRepositoryImpl
import com.localfriday.app.domain.memory.KnowledgeRepository
import com.localfriday.app.domain.memory.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MemoryModule {

    @Binds
    @Singleton
    abstract fun bindKnowledgeRepository(
        impl: KnowledgeRepositoryImpl
    ): KnowledgeRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        impl: TaskRepositoryImpl
    ): TaskRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        impl: com.localfriday.app.data.local.repository.ProfileRepositoryImpl
    ): com.localfriday.app.domain.memory.ProfileRepository

    @Binds
    @Singleton
    abstract fun bindAuditRepository(
        impl: com.localfriday.app.data.local.repository.AuditRepositoryImpl
    ): com.localfriday.app.domain.memory.AuditRepository

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        impl: com.localfriday.app.data.local.repository.ConversationRepositoryImpl
    ): com.localfriday.app.domain.memory.ConversationRepository
}
