package com.localfriday.app.di

import com.localfriday.app.data.local.repository.AuditRepositoryImpl
import com.localfriday.app.data.local.repository.ConversationRepositoryImpl
import com.localfriday.app.data.local.repository.ProfileRepositoryImpl
import com.localfriday.app.domain.memory.AuditRepository
import com.localfriday.app.domain.memory.ConversationRepository
import com.localfriday.app.domain.memory.ProfileRepository
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
    abstract fun bindProfileRepository(
        profileRepositoryImpl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        conversationRepositoryImpl: ConversationRepositoryImpl
    ): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindAuditRepository(
        auditRepositoryImpl: AuditRepositoryImpl
    ): AuditRepository
}
