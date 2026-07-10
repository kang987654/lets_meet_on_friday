package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * [v0] 사용자 프로필 저장소
 * 시간 변환이나 Enum 매핑과 같은 mapper 책임은 구현체(Impl)에서 일관되게 처리해야 합니다.
 */
interface ProfileRepository {
    fun getProfile(): Flow<UserProfile?>
    suspend fun saveProfile(profile: UserProfile): AppResult<Unit>
}
