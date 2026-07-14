package com.kosmos.app.data.di

import com.kosmos.app.data.local.file.ExportImportManager
import com.kosmos.app.domain.memory.MemoryBackupManager

import com.kosmos.app.data.local.repository.KnowledgeRepositoryImpl
import com.kosmos.app.data.local.repository.TaskRepositoryImpl
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.TaskRepository
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
        impl: com.kosmos.app.data.local.repository.ProfileRepositoryImpl
    ): com.kosmos.app.domain.memory.ProfileRepository

    @Binds
    @Singleton
    abstract fun bindAuditRepository(
        impl: com.kosmos.app.data.local.repository.AuditRepositoryImpl
    ): com.kosmos.app.domain.memory.AuditRepository

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        impl: com.kosmos.app.data.local.repository.ConversationRepositoryImpl
    ): com.kosmos.app.domain.memory.ConversationRepository

    @Binds
    @Singleton
    abstract fun bindMemoryBackupManager(
        impl: ExportImportManager
    ): MemoryBackupManager
}
