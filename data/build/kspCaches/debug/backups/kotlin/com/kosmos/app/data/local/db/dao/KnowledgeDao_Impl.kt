package com.kosmos.app.`data`.local.db.dao

import androidx.paging.PagingSource
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.RoomRawQuery
import androidx.room.paging.LimitOffsetPagingSource
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.kosmos.app.`data`.local.db.entity.KnowledgeEntity
import javax.`annotation`.processing.Generated
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
public class KnowledgeDao_Impl(
  __db: RoomDatabase,
) : KnowledgeDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfKnowledgeEntity: EntityInsertAdapter<KnowledgeEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfKnowledgeEntity = object : EntityInsertAdapter<KnowledgeEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `knowledge_note` (`id`,`content`,`sourceSessionId`,`tags`,`createdAt`,`updatedAt`) VALUES (?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: KnowledgeEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.content)
        val _tmpSourceSessionId: String? = entity.sourceSessionId
        if (_tmpSourceSessionId == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpSourceSessionId)
        }
        statement.bindText(4, entity.tags)
        statement.bindLong(5, entity.createdAt)
        statement.bindLong(6, entity.updatedAt)
      }
    }
  }

  public override suspend fun insert(knowledge: KnowledgeEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfKnowledgeEntity.insert(_connection, knowledge)
  }

  public override suspend fun search(query: String, limit: Int): List<KnowledgeEntity> {
    val _sql: String = "SELECT * FROM knowledge_note WHERE content LIKE '%' || ? || '%' ORDER BY createdAt DESC LIMIT ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        _argIndex = 2
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfSourceSessionId: Int = getColumnIndexOrThrow(_stmt, "sourceSessionId")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _result: MutableList<KnowledgeEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: KnowledgeEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpSourceSessionId: String?
          if (_stmt.isNull(_columnIndexOfSourceSessionId)) {
            _tmpSourceSessionId = null
          } else {
            _tmpSourceSessionId = _stmt.getText(_columnIndexOfSourceSessionId)
          }
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item = KnowledgeEntity(_tmpId,_tmpContent,_tmpSourceSessionId,_tmpTags,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun searchRecent(limit: Int): List<KnowledgeEntity> {
    val _sql: String = "SELECT * FROM knowledge_note ORDER BY createdAt DESC LIMIT ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfSourceSessionId: Int = getColumnIndexOrThrow(_stmt, "sourceSessionId")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _result: MutableList<KnowledgeEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: KnowledgeEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpSourceSessionId: String?
          if (_stmt.isNull(_columnIndexOfSourceSessionId)) {
            _tmpSourceSessionId = null
          } else {
            _tmpSourceSessionId = _stmt.getText(_columnIndexOfSourceSessionId)
          }
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item = KnowledgeEntity(_tmpId,_tmpContent,_tmpSourceSessionId,_tmpTags,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun searchByTags(tag: String, limit: Int): List<KnowledgeEntity> {
    val _sql: String = "SELECT * FROM knowledge_note WHERE tags LIKE '%' || ? || '%' ORDER BY createdAt DESC LIMIT ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, tag)
        _argIndex = 2
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfSourceSessionId: Int = getColumnIndexOrThrow(_stmt, "sourceSessionId")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _result: MutableList<KnowledgeEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: KnowledgeEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpSourceSessionId: String?
          if (_stmt.isNull(_columnIndexOfSourceSessionId)) {
            _tmpSourceSessionId = null
          } else {
            _tmpSourceSessionId = _stmt.getText(_columnIndexOfSourceSessionId)
          }
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item = KnowledgeEntity(_tmpId,_tmpContent,_tmpSourceSessionId,_tmpTags,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getPaged(): PagingSource<Int, KnowledgeEntity> {
    val _sql: String = "SELECT * FROM knowledge_note ORDER BY createdAt DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql)
    return object : LimitOffsetPagingSource<KnowledgeEntity>(_rawQuery, __db, "knowledge_note") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<KnowledgeEntity> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
          val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
          val _columnIndexOfSourceSessionId: Int = getColumnIndexOrThrow(_stmt, "sourceSessionId")
          val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
          val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
          val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
          val _result: MutableList<KnowledgeEntity> = mutableListOf()
          while (_stmt.step()) {
            val _item: KnowledgeEntity
            val _tmpId: String
            _tmpId = _stmt.getText(_columnIndexOfId)
            val _tmpContent: String
            _tmpContent = _stmt.getText(_columnIndexOfContent)
            val _tmpSourceSessionId: String?
            if (_stmt.isNull(_columnIndexOfSourceSessionId)) {
              _tmpSourceSessionId = null
            } else {
              _tmpSourceSessionId = _stmt.getText(_columnIndexOfSourceSessionId)
            }
            val _tmpTags: String
            _tmpTags = _stmt.getText(_columnIndexOfTags)
            val _tmpCreatedAt: Long
            _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
            val _tmpUpdatedAt: Long
            _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
            _item = KnowledgeEntity(_tmpId,_tmpContent,_tmpSourceSessionId,_tmpTags,_tmpCreatedAt,_tmpUpdatedAt)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override suspend fun delete(noteId: String) {
    val _sql: String = "DELETE FROM knowledge_note WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, noteId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
