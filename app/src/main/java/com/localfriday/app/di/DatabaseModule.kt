package com.localfriday.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.localfriday.app.data.local.db.LocalFridayDatabase
import com.localfriday.app.data.local.db.dao.AuditDao
import com.localfriday.app.data.local.db.dao.ConversationDao
import com.localfriday.app.data.local.db.dao.KnowledgeDao
import com.localfriday.app.data.local.db.dao.ProfileDao
import com.localfriday.app.data.local.db.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLocalFridayDatabase(@ApplicationContext context: Context): LocalFridayDatabase {
        return Room.databaseBuilder(
            context,
            LocalFridayDatabase::class.java,
            "localfriday_db"
        )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigration() // For development MVP phase
        .build()
    }

    @Provides
    fun provideProfileDao(database: LocalFridayDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideConversationDao(database: LocalFridayDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideAuditDao(database: LocalFridayDatabase): AuditDao = database.auditDao()

    @Provides
    fun provideTaskDao(database: LocalFridayDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideKnowledgeDao(database: LocalFridayDatabase): KnowledgeDao = database.knowledgeDao()
}
