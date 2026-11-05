package com.kosmos.app.data.di

import com.kosmos.app.data.local.file.ExportImportManager
import com.kosmos.app.domain.memory.MemoryBackupManager

import com.kosmos.app.data.local.repository.KnowledgeRepositoryImpl
import com.kosmos.app.data.local.repository.TaskRepositoryImpl
import com.kosmos.app.data.local.repository.MemoryVectorRepositoryImpl
import com.kosmos.app.domain.memory.MemoryVectorRepository
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.kosmos.app.data.local.repository.AuditRepositoryImpl
import com.kosmos.app.data.local.repository.ConversationRepositoryImpl
import com.kosmos.app.data.local.repository.DataResetRepositoryImpl
import com.kosmos.app.data.local.repository.LibraryRepositoryImpl
import com.kosmos.app.domain.library.LibraryRepository
import com.kosmos.app.data.local.repository.EpisodeRepositoryImpl
import com.kosmos.app.data.local.repository.ProfileRepositoryImpl
import com.kosmos.app.data.local.repository.ProfileSuggestionRepositoryImpl
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.DataResetRepository
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.memory.ProfileRepository
import com.kosmos.app.domain.memory.ProfileSuggestionRepository

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
    abstract fun bindMemoryVectorRepository(
        impl: MemoryVectorRepositoryImpl
    ): MemoryVectorRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        impl: TaskRepositoryImpl
    ): TaskRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(
        impl: LibraryRepositoryImpl
    ): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindDataResetRepository(
        impl: DataResetRepositoryImpl
    ): DataResetRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        impl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindProfileSuggestionRepository(
        impl: ProfileSuggestionRepositoryImpl
    ): ProfileSuggestionRepository

    @Binds
    @Singleton
    abstract fun bindAuditRepository(
        impl: AuditRepositoryImpl
    ): AuditRepository

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        impl: ConversationRepositoryImpl
    ): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindMemoryBackupManager(
        impl: ExportImportManager
    ): MemoryBackupManager

    @Binds
    @Singleton
    abstract fun bindEpisodeRepository(
        impl: EpisodeRepositoryImpl
    ): EpisodeRepository
}
