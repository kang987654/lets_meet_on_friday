package com.kosmos.app.`data`.local.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.kosmos.app.`data`.local.db.dao.AuditDao
import com.kosmos.app.`data`.local.db.dao.AuditDao_Impl
import com.kosmos.app.`data`.local.db.dao.ConversationDao
import com.kosmos.app.`data`.local.db.dao.ConversationDao_Impl
import com.kosmos.app.`data`.local.db.dao.KnowledgeDao
import com.kosmos.app.`data`.local.db.dao.KnowledgeDao_Impl
import com.kosmos.app.`data`.local.db.dao.ProfileDao
import com.kosmos.app.`data`.local.db.dao.ProfileDao_Impl
import com.kosmos.app.`data`.local.db.dao.TaskDao
import com.kosmos.app.`data`.local.db.dao.TaskDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class KosmosDatabase_Impl : KosmosDatabase() {
  private val _profileDao: Lazy<ProfileDao> = lazy {
    ProfileDao_Impl(this)
  }

  private val _conversationDao: Lazy<ConversationDao> = lazy {
    ConversationDao_Impl(this)
  }

  private val _auditDao: Lazy<AuditDao> = lazy {
    AuditDao_Impl(this)
  }

  private val _taskDao: Lazy<TaskDao> = lazy {
    TaskDao_Impl(this)
  }

  private val _knowledgeDao: Lazy<KnowledgeDao> = lazy {
    KnowledgeDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(2, "835b9612fa220f7c8b5a441bffb27d09", "b8cb8ecb3b4c2298b26fdf3340755dbe") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `profile` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `style` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `conversation` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `inputType` TEXT NOT NULL, `searchUsed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_sessionId` ON `conversation` (`sessionId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_createdAt` ON `conversation` (`createdAt`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `audit_log` (`id` TEXT NOT NULL, `eventType` TEXT NOT NULL, `details` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_eventType` ON `audit_log` (`eventType`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_timestamp` ON `audit_log` (`timestamp`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `task_item` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER, `dueDateIso` TEXT, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_task_item_isCompleted` ON `task_item` (`isCompleted`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `knowledge_note` (`id` TEXT NOT NULL, `content` TEXT NOT NULL, `sourceSessionId` TEXT, `tags` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_note_createdAt` ON `knowledge_note` (`createdAt`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '835b9612fa220f7c8b5a441bffb27d09')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `profile`")
        connection.execSQL("DROP TABLE IF EXISTS `conversation`")
        connection.execSQL("DROP TABLE IF EXISTS `audit_log`")
        connection.execSQL("DROP TABLE IF EXISTS `task_item`")
        connection.execSQL("DROP TABLE IF EXISTS `knowledge_note`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsProfile: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsProfile.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProfile.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProfile.put("style", TableInfo.Column("style", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProfile.put("updatedAt", TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysProfile: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesProfile: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoProfile: TableInfo = TableInfo("profile", _columnsProfile, _foreignKeysProfile, _indicesProfile)
        val _existingProfile: TableInfo = read(connection, "profile")
        if (!_infoProfile.equals(_existingProfile)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |profile(com.kosmos.app.data.local.db.entity.ProfileEntity).
              | Expected:
              |""".trimMargin() + _infoProfile + """
              |
              | Found:
              |""".trimMargin() + _existingProfile)
        }
        val _columnsConversation: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsConversation.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversation.put("sessionId", TableInfo.Column("sessionId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversation.put("role", TableInfo.Column("role", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversation.put("content", TableInfo.Column("content", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversation.put("inputType", TableInfo.Column("inputType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversation.put("searchUsed", TableInfo.Column("searchUsed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsConversation.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysConversation: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesConversation: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesConversation.add(TableInfo.Index("index_conversation_sessionId", false, listOf("sessionId"), listOf("ASC")))
        _indicesConversation.add(TableInfo.Index("index_conversation_createdAt", false, listOf("createdAt"), listOf("ASC")))
        val _infoConversation: TableInfo = TableInfo("conversation", _columnsConversation, _foreignKeysConversation, _indicesConversation)
        val _existingConversation: TableInfo = read(connection, "conversation")
        if (!_infoConversation.equals(_existingConversation)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |conversation(com.kosmos.app.data.local.db.entity.ConversationEntity).
              | Expected:
              |""".trimMargin() + _infoConversation + """
              |
              | Found:
              |""".trimMargin() + _existingConversation)
        }
        val _columnsAuditLog: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsAuditLog.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAuditLog.put("eventType", TableInfo.Column("eventType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAuditLog.put("details", TableInfo.Column("details", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAuditLog.put("sessionId", TableInfo.Column("sessionId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAuditLog.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysAuditLog: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesAuditLog: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesAuditLog.add(TableInfo.Index("index_audit_log_eventType", false, listOf("eventType"), listOf("ASC")))
        _indicesAuditLog.add(TableInfo.Index("index_audit_log_timestamp", false, listOf("timestamp"), listOf("ASC")))
        val _infoAuditLog: TableInfo = TableInfo("audit_log", _columnsAuditLog, _foreignKeysAuditLog, _indicesAuditLog)
        val _existingAuditLog: TableInfo = read(connection, "audit_log")
        if (!_infoAuditLog.equals(_existingAuditLog)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |audit_log(com.kosmos.app.data.local.db.entity.AuditEntity).
              | Expected:
              |""".trimMargin() + _infoAuditLog + """
              |
              | Found:
              |""".trimMargin() + _existingAuditLog)
        }
        val _columnsTaskItem: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTaskItem.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTaskItem.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTaskItem.put("isCompleted", TableInfo.Column("isCompleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTaskItem.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTaskItem.put("completedAt", TableInfo.Column("completedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTaskItem.put("dueDateIso", TableInfo.Column("dueDateIso", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTaskItem: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTaskItem: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesTaskItem.add(TableInfo.Index("index_task_item_isCompleted", false, listOf("isCompleted"), listOf("ASC")))
        val _infoTaskItem: TableInfo = TableInfo("task_item", _columnsTaskItem, _foreignKeysTaskItem, _indicesTaskItem)
        val _existingTaskItem: TableInfo = read(connection, "task_item")
        if (!_infoTaskItem.equals(_existingTaskItem)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |task_item(com.kosmos.app.data.local.db.entity.TaskEntity).
              | Expected:
              |""".trimMargin() + _infoTaskItem + """
              |
              | Found:
              |""".trimMargin() + _existingTaskItem)
        }
        val _columnsKnowledgeNote: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsKnowledgeNote.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsKnowledgeNote.put("content", TableInfo.Column("content", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsKnowledgeNote.put("sourceSessionId", TableInfo.Column("sourceSessionId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsKnowledgeNote.put("tags", TableInfo.Column("tags", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsKnowledgeNote.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsKnowledgeNote.put("updatedAt", TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysKnowledgeNote: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesKnowledgeNote: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesKnowledgeNote.add(TableInfo.Index("index_knowledge_note_createdAt", false, listOf("createdAt"), listOf("ASC")))
        val _infoKnowledgeNote: TableInfo = TableInfo("knowledge_note", _columnsKnowledgeNote, _foreignKeysKnowledgeNote, _indicesKnowledgeNote)
        val _existingKnowledgeNote: TableInfo = read(connection, "knowledge_note")
        if (!_infoKnowledgeNote.equals(_existingKnowledgeNote)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |knowledge_note(com.kosmos.app.data.local.db.entity.KnowledgeEntity).
              | Expected:
              |""".trimMargin() + _infoKnowledgeNote + """
              |
              | Found:
              |""".trimMargin() + _existingKnowledgeNote)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "profile", "conversation", "audit_log", "task_item", "knowledge_note")
  }

  public override fun clearAllTables() {
    super.performClear(false, "profile", "conversation", "audit_log", "task_item", "knowledge_note")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(ProfileDao::class, ProfileDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ConversationDao::class, ConversationDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(AuditDao::class, AuditDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(TaskDao::class, TaskDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(KnowledgeDao::class, KnowledgeDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun profileDao(): ProfileDao = _profileDao.value

  public override fun conversationDao(): ConversationDao = _conversationDao.value

  public override fun auditDao(): AuditDao = _auditDao.value

  public override fun taskDao(): TaskDao = _taskDao.value

  public override fun knowledgeDao(): KnowledgeDao = _knowledgeDao.value
}
