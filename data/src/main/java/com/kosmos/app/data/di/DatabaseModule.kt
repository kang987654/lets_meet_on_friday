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

    // [WHY] 일정 종료 시각/설명 보존(v4)을 위한 스키마 확장. 사용자 메모리가 핵심 가치인 앱이므로
    // 파괴적 마이그레이션 대신 명시적 Migration으로 데이터를 보존한다.
    private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE task_item ADD COLUMN endDateIso TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE task_item ADD COLUMN description TEXT DEFAULT NULL")
        }
    }

    @Provides
    @Singleton
    fun provideKosmosDatabase(@ApplicationContext context: Context): KosmosDatabase {
        return Room.databaseBuilder(
            context,
            KosmosDatabase::class.java,
            Constants.DATABASE_NAME
        )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addMigrations(MIGRATION_3_4)
        .fallbackToDestructiveMigrationOnDowngrade() // [WHY] 업그레이드 경로는 항상 명시적 Migration을 요구한다
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
