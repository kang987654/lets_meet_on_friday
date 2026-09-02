package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.runCatchingCancellable
import com.kosmos.app.data.local.db.dao.ProfileSuggestionDao
import com.kosmos.app.data.local.db.entity.ProfileSuggestionEntity
import com.kosmos.app.domain.memory.ProfileSuggestionRepository
import com.kosmos.app.domain.model.ProfileSuggestion
import com.kosmos.app.domain.model.ProfileSuggestionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProfileSuggestionRepositoryImpl @Inject constructor(
    private val dao: ProfileSuggestionDao
) : ProfileSuggestionRepository {

    override fun observePending(): Flow<List<ProfileSuggestion>> =
        dao.observeByStatus(ProfileSuggestionStatus.PENDING.name)
            .map { entities -> entities.map { it.toDomain() } }
            // [WHY] 채팅 화면이 구독한다 — DB 오류가 채팅을 죽이면 안 된다 (ProfileRepositoryImpl 전례).
            .catch { emit(emptyList()) }

    override suspend fun insert(suggestion: ProfileSuggestion): AppResult<Unit> = runCatchingCancellable {
        dao.insert(
            ProfileSuggestionEntity(
                id = suggestion.id,
                key = suggestion.key,
                value = suggestion.value,
                episodeId = suggestion.episodeId,
                status = suggestion.status.name,
                createdAt = suggestion.createdAt,
                updatedAt = suggestion.updatedAt
            )
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Failure(AppError.DbWriteError("profile_suggestion")) }
    )

    override suspend fun updateStatus(
        id: String,
        status: ProfileSuggestionStatus,
        updatedAt: Long
    ): AppResult<Unit> = runCatchingCancellable {
        dao.updateStatus(id, status.name, updatedAt)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Failure(AppError.DbWriteError("profile_suggestion")) }
    )

    // [WHY] 조회 실패는 "없음"으로 강등한다 — 추출기가 중복 검사 실패로 멈추는 것보다
    // 드물게 한 번 더 묻는 쪽이 낫다.
    override suspend fun exists(key: String, value: String): Boolean =
        runCatchingCancellable { dao.countByKeyValue(key, value) > 0 }.getOrDefault(false)

    private fun ProfileSuggestionEntity.toDomain() = ProfileSuggestion(
        id = id,
        key = key,
        value = value,
        episodeId = episodeId,
        // [WHY] 알 수 없는 상태 문자열은 PENDING 이 아니라 REJECTED 로 읽는다 — 다운그레이드
        // 등으로 깨진 행이 카드로 튀어나오지 않게 한다.
        status = runCatching { ProfileSuggestionStatus.valueOf(status) }
            .getOrDefault(ProfileSuggestionStatus.REJECTED),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
