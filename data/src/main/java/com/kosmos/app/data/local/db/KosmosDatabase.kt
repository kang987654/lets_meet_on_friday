package com.kosmos.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kosmos.app.data.local.db.dao.LibraryDao
import com.kosmos.app.data.local.db.entity.LibraryChunkEntity
import com.kosmos.app.data.local.db.entity.LibraryDocumentEntity
import com.kosmos.app.data.local.db.entity.LibraryFtsEntity
import com.kosmos.app.data.local.db.entity.LibraryVectorEntity
import com.kosmos.app.data.local.db.entity.MemoryVectorEntity
import com.kosmos.app.data.local.db.dao.MemoryVectorDao
import com.kosmos.app.data.local.db.dao.AuditDao
import com.kosmos.app.data.local.db.dao.ConversationDao
import com.kosmos.app.data.local.db.dao.KnowledgeDao
import com.kosmos.app.data.local.db.dao.ProfileDao
import com.kosmos.app.data.local.db.dao.TaskDao
import com.kosmos.app.data.local.db.entity.AuditEntity
import com.kosmos.app.data.local.db.entity.ConversationEntity
import com.kosmos.app.data.local.db.entity.KnowledgeEntity
import com.kosmos.app.data.local.db.entity.ProfileEntryEntity
import com.kosmos.app.data.local.db.entity.TaskEntity
import com.kosmos.app.data.local.db.dao.EpisodeDao
import com.kosmos.app.data.local.db.dao.ProfileSuggestionDao
import com.kosmos.app.data.local.db.entity.EpisodeEntity
import com.kosmos.app.data.local.db.entity.ProfileSuggestionEntity

@Database(
    entities = [
        ProfileEntryEntity::class,
        ConversationEntity::class,
        AuditEntity::class,
        TaskEntity::class,
        KnowledgeEntity::class,
        EpisodeEntity::class,
        ProfileSuggestionEntity::class,
        LibraryDocumentEntity::class,
        LibraryChunkEntity::class,
        LibraryFtsEntity::class,
        LibraryVectorEntity::class,
        MemoryVectorEntity::class
    ],
    version = KosmosDatabase.SCHEMA_VERSION,
    exportSchema = true
)
abstract class KosmosDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun conversationDao(): ConversationDao
    abstract fun auditDao(): AuditDao
    abstract fun taskDao(): TaskDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun profileSuggestionDao(): ProfileSuggestionDao
    abstract fun libraryDao(): LibraryDao
    abstract fun memoryVectorDao(): MemoryVectorDao

    companion object {
        /**
         * 현재 스키마 버전 — `@Database(version)` 과 백업 가져오기 검증의 단일 출처.
         * 올릴 때는 KosmosMigrations 에 경로를 추가하고 `data/schemas/` 에 JSON 이 생기는지 확인한다.
         */
        const val SCHEMA_VERSION = 13
    }
}
