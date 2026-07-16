package com.kosmos.app.`data`.local.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.kosmos.app.`data`.local.db.entity.ConversationEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ConversationDao_Impl(
  __db: RoomDatabase,
) : ConversationDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfConversationEntity: EntityInsertAdapter<ConversationEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfConversationEntity = object : EntityInsertAdapter<ConversationEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `conversation` (`id`,`sessionId`,`role`,`content`,`inputType`,`searchUsed`,`createdAt`) VALUES (?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ConversationEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.sessionId)
        statement.bindText(3, entity.role)
        statement.bindText(4, entity.content)
        statement.bindText(5, entity.inputType)
        val _tmp: Int = if (entity.searchUsed) 1 else 0
        statement.bindLong(6, _tmp.toLong())
        statement.bindLong(7, entity.createdAt)
      }
    }
  }

  public override suspend fun insert(conversation: ConversationEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfConversationEntity.insert(_connection, conversation)
  }

  public override suspend fun getRecentBySession(sessionId: String, limit: Int): List<ConversationEntity> {
    val _sql: String = "SELECT * FROM conversation WHERE sessionId = ? ORDER BY createdAt DESC LIMIT ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "sessionId")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfInputType: Int = getColumnIndexOrThrow(_stmt, "inputType")
        val _columnIndexOfSearchUsed: Int = getColumnIndexOrThrow(_stmt, "searchUsed")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<ConversationEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ConversationEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpSessionId: String
          _tmpSessionId = _stmt.getText(_columnIndexOfSessionId)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpInputType: String
          _tmpInputType = _stmt.getText(_columnIndexOfInputType)
          val _tmpSearchUsed: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfSearchUsed).toInt()
          _tmpSearchUsed = _tmp != 0
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = ConversationEntity(_tmpId,_tmpSessionId,_tmpRole,_tmpContent,_tmpInputType,_tmpSearchUsed,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPagedBySession(
    sessionId: String,
    offset: Int,
    limit: Int,
  ): List<ConversationEntity> {
    val _sql: String = "SELECT * FROM conversation WHERE sessionId = ? ORDER BY createdAt DESC LIMIT ? OFFSET ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, sessionId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, limit.toLong())
        _argIndex = 3
        _stmt.bindLong(_argIndex, offset.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "sessionId")
        val _columnIndexOfRole: Int = getColumnIndexOrThrow(_stmt, "role")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfInputType: Int = getColumnIndexOrThrow(_stmt, "inputType")
        val _columnIndexOfSearchUsed: Int = getColumnIndexOrThrow(_stmt, "searchUsed")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<ConversationEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ConversationEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpSessionId: String
          _tmpSessionId = _stmt.getText(_columnIndexOfSessionId)
          val _tmpRole: String
          _tmpRole = _stmt.getText(_columnIndexOfRole)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpInputType: String
          _tmpInputType = _stmt.getText(_columnIndexOfInputType)
          val _tmpSearchUsed: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfSearchUsed).toInt()
          _tmpSearchUsed = _tmp != 0
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = ConversationEntity(_tmpId,_tmpSessionId,_tmpRole,_tmpContent,_tmpInputType,_tmpSearchUsed,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
