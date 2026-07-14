package com.kosmos.app.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kosmos.app.data.local.db.KosmosDatabase
import com.kosmos.app.data.local.db.dao.AuditDao
import com.kosmos.app.data.local.db.dao.ConversationDao
import com.kosmos.app.data.local.db.dao.KnowledgeDao
import com.kosmos.app.data.local.db.dao.ProfileDao
import com.kosmos.app.data.local.db.dao.TaskDao
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
    fun provideKosmosDatabase(@ApplicationContext context: Context): KosmosDatabase {
        return Room.databaseBuilder(
            context,
            KosmosDatabase::class.java,
            "kosmos_db"
        )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigration() // For development MVP phase
        .build()
    }

    @Provides
    fun provideProfileDao(database: KosmosDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideConversationDao(database: KosmosDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideAuditDao(database: KosmosDatabase): AuditDao = database.auditDao()

    @Provides
    fun provideTaskDao(database: KosmosDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideKnowledgeDao(database: KosmosDatabase): KnowledgeDao = database.knowledgeDao()
}
