package com.kosmos.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kosmos.app.data.local.db.entity.ProfileEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    // [WHY] 키 사전순 고정 — 렌더된 프로필 블록이 바이트-안정이어야 시스템 지시 비교가
    // 불필요한 재프리필을 만들지 않는다 (renderProfileBlock 과 같은 정렬).
    @Query("SELECT * FROM profile ORDER BY `key`")
    fun observeAll(): Flow<List<ProfileEntryEntity>>

    @Query("SELECT * FROM profile ORDER BY `key`")
    suspend fun getAll(): List<ProfileEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ProfileEntryEntity)

    @Query("DELETE FROM profile WHERE `key` = :key")
    suspend fun delete(key: String)
}
