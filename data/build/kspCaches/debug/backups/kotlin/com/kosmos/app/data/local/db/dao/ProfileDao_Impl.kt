package com.kosmos.app.`data`.local.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.kosmos.app.`data`.local.db.entity.ProfileEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ProfileDao_Impl(
  __db: RoomDatabase,
) : ProfileDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfProfileEntity: EntityInsertAdapter<ProfileEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfProfileEntity = object : EntityInsertAdapter<ProfileEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `profile` (`id`,`name`,`style`,`updatedAt`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ProfileEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.style)
        statement.bindLong(4, entity.updatedAt)
      }
    }
  }

  public override suspend fun insertOrUpdateProfile(profile: ProfileEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfProfileEntity.insert(_connection, profile)
  }

  public override fun getProfileFlow(profileId: String): Flow<ProfileEntity?> {
    val _sql: String = "SELECT * FROM profile WHERE id = ? LIMIT 1"
    return createFlow(__db, false, arrayOf("profile")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, profileId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfStyle: Int = getColumnIndexOrThrow(_stmt, "style")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _result: ProfileEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpStyle: String
          _tmpStyle = _stmt.getText(_columnIndexOfStyle)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result = ProfileEntity(_tmpId,_tmpName,_tmpStyle,_tmpUpdatedAt)
        } else {
          _result = null
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
