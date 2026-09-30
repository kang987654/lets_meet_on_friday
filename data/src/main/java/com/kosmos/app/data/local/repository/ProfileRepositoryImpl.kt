package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.dao.ProfileDao
import com.kosmos.app.data.local.db.entity.ProfileEntryEntity
import com.kosmos.app.domain.memory.ProfileRepository
import com.kosmos.app.domain.model.ProfileEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val dao: ProfileDao
) : ProfileRepository {

    override fun observeEntries(): Flow<List<ProfileEntry>> =
        dao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            // [WHY] 드로어 카드가 구독한다 — DB 오류가 드로어를 죽이면 안 되고,
            // 빈 목록이면 카드가 "등록해 보세요" 상태로 정직하게 강등된다.
            .catch { emit(emptyList()) }

    override suspend fun getEntries(): AppResult<List<ProfileEntry>> = dbRead(TABLE, "getEntries") {
        dao.getAll().map { it.toDomain() }
    }

    override suspend fun upsert(key: String, value: String, source: String): AppResult<Unit> = dbWrite(TABLE, "upsert") {
            dao.upsert(
                ProfileEntryEntity(
                    key = key,
                    value = value,
                    source = source,
                    updatedAt = System.currentTimeMillis()
                )
            )
    }

    override suspend fun delete(key: String): AppResult<Unit> = dbWrite(TABLE, "delete") {
        dao.delete(key)
    }

    private fun ProfileEntryEntity.toDomain() = ProfileEntry(
        key = key,
        value = value,
        source = source,
        updatedAt = updatedAt
    )

    private companion object {
        const val TABLE = "profile"
    }
}
