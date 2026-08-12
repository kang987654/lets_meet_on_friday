package com.kosmos.app.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kosmos.app.data.local.db.KosmosDatabase
import com.kosmos.app.data.local.db.KosmosMigrations
import com.kosmos.app.data.local.db.dao.AuditDao
import com.kosmos.app.data.local.db.dao.ConversationDao
import com.kosmos.app.data.local.db.dao.KnowledgeDao
import com.kosmos.app.data.local.db.dao.ProfileDao
import com.kosmos.app.data.local.db.dao.TaskDao
import com.kosmos.app.core.common.Constants
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
            Constants.DATABASE_NAME
        )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        // [WHY] 마이그레이션 정의는 KosmosMigrations 로 분리했다 — 인라인 익명 객체로는
        // MigrationTestHelper 검증이 불가능했다.
        .addMigrations(*KosmosMigrations.ALL)
        // [WHY] 업그레이드 경로는 항상 명시적 Migration을 요구한다. 다운그레이드에서만 파괴적
        // 재생성을 허용하며, 이 DB 는 전부 Room 이 관리하므로 dropAllTables = true 로 잔여
        // 테이블 없이 지운다.
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
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
