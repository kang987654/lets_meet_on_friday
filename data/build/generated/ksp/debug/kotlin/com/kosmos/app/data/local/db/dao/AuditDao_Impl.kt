package com.kosmos.app.`data`.local.db.dao

import androidx.paging.PagingSource
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.RoomRawQuery
import androidx.room.paging.LimitOffsetPagingSource
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.kosmos.app.`data`.local.db.entity.AuditEntity
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
public class AuditDao_Impl(
  __db: RoomDatabase,
) : AuditDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfAuditEntity: EntityInsertAdapter<AuditEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfAuditEntity = object : EntityInsertAdapter<AuditEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `audit_log` (`id`,`eventType`,`details`,`sessionId`,`timestamp`) VALUES (?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: AuditEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.eventType)
        statement.bindText(3, entity.details)
        statement.bindText(4, entity.sessionId)
        statement.bindLong(5, entity.timestamp)
      }
    }
  }

  public override suspend fun insert(audit: AuditEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfAuditEntity.insert(_connection, audit)
  }

  public override fun getPaged(): PagingSource<Int, AuditEntity> {
    val _sql: String = "SELECT * FROM audit_log ORDER BY timestamp DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql)
    return object : LimitOffsetPagingSource<AuditEntity>(_rawQuery, __db, "audit_log") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<AuditEntity> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
          val _columnIndexOfEventType: Int = getColumnIndexOrThrow(_stmt, "eventType")
          val _columnIndexOfDetails: Int = getColumnIndexOrThrow(_stmt, "details")
          val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "sessionId")
          val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
          val _result: MutableList<AuditEntity> = mutableListOf()
          while (_stmt.step()) {
            val _item: AuditEntity
            val _tmpId: String
            _tmpId = _stmt.getText(_columnIndexOfId)
            val _tmpEventType: String
            _tmpEventType = _stmt.getText(_columnIndexOfEventType)
            val _tmpDetails: String
            _tmpDetails = _stmt.getText(_columnIndexOfDetails)
            val _tmpSessionId: String
            _tmpSessionId = _stmt.getText(_columnIndexOfSessionId)
            val _tmpTimestamp: Long
            _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
            _item = AuditEntity(_tmpId,_tmpEventType,_tmpDetails,_tmpSessionId,_tmpTimestamp)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override fun getPagedByType(eventType: String): PagingSource<Int, AuditEntity> {
    val _sql: String = "SELECT * FROM audit_log WHERE eventType = ? ORDER BY timestamp DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql) { _stmt ->
      var _argIndex: Int = 1
      _stmt.bindText(_argIndex, eventType)
    }
    return object : LimitOffsetPagingSource<AuditEntity>(_rawQuery, __db, "audit_log") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<AuditEntity> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
          val _columnIndexOfEventType: Int = getColumnIndexOrThrow(_stmt, "eventType")
          val _columnIndexOfDetails: Int = getColumnIndexOrThrow(_stmt, "details")
          val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "sessionId")
          val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
          val _result: MutableList<AuditEntity> = mutableListOf()
          while (_stmt.step()) {
            val _item: AuditEntity
            val _tmpId: String
            _tmpId = _stmt.getText(_columnIndexOfId)
            val _tmpEventType: String
            _tmpEventType = _stmt.getText(_columnIndexOfEventType)
            val _tmpDetails: String
            _tmpDetails = _stmt.getText(_columnIndexOfDetails)
            val _tmpSessionId: String
            _tmpSessionId = _stmt.getText(_columnIndexOfSessionId)
            val _tmpTimestamp: Long
            _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
            _item = AuditEntity(_tmpId,_tmpEventType,_tmpDetails,_tmpSessionId,_tmpTimestamp)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
