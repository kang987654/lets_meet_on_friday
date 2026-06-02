package com.localfriday.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.localfriday.app.data.local.db.dao.AuditDao
import com.localfriday.app.data.local.db.dao.ConversationDao
import com.localfriday.app.data.local.db.dao.KnowledgeDao
import com.localfriday.app.data.local.db.dao.ProfileDao
import com.localfriday.app.data.local.db.dao.TaskDao
import com.localfriday.app.data.local.db.entity.AuditEntity
import com.localfriday.app.data.local.db.entity.ConversationEntity
import com.localfriday.app.data.local.db.entity.KnowledgeEntity
import com.localfriday.app.data.local.db.entity.ProfileEntity
import com.localfriday.app.data.local.db.entity.TaskEntity

@Database(
    entities = [
        ProfileEntity::class,
        ConversationEntity::class,
        AuditEntity::class,
        TaskEntity::class,
        KnowledgeEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LocalFridayDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun conversationDao(): ConversationDao
    abstract fun auditDao(): AuditDao
    abstract fun taskDao(): TaskDao
    abstract fun knowledgeDao(): KnowledgeDao
}
