package com.kosmos.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kosmos.app.data.local.db.dao.AuditDao
import com.kosmos.app.data.local.db.dao.ConversationDao
import com.kosmos.app.data.local.db.dao.KnowledgeDao
import com.kosmos.app.data.local.db.dao.ProfileDao
import com.kosmos.app.data.local.db.dao.TaskDao
import com.kosmos.app.data.local.db.entity.AuditEntity
import com.kosmos.app.data.local.db.entity.ConversationEntity
import com.kosmos.app.data.local.db.entity.KnowledgeEntity
import com.kosmos.app.data.local.db.entity.ProfileEntity
import com.kosmos.app.data.local.db.entity.TaskEntity

@Database(
    entities = [
        ProfileEntity::class,
        ConversationEntity::class,
        AuditEntity::class,
        TaskEntity::class,
        KnowledgeEntity::class,
        com.kosmos.app.data.local.db.entity.EpisodeEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class KosmosDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun conversationDao(): ConversationDao
    abstract fun auditDao(): AuditDao
    abstract fun taskDao(): TaskDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun episodeDao(): com.kosmos.app.data.local.db.dao.EpisodeDao
}
