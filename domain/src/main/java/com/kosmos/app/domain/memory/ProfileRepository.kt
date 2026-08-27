package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.ProfileEntry
import kotlinx.coroutines.flow.Flow

/**
 * 프로필 키-값 저장소 — 시스템 지시 상시 주입용 소량 기억 (C′1, AC6 정식 배선).
 *
 * [WHY] v0 의 `UserProfile(name, style)` 계약은 호출처 0곳의 사문이었고, 빈 프로필을
 * 구분할 수 없는 시그니처(null 미방출)라 상시 주입에 못 쓰였다 — 0.23.0 에서 교체.
 */
interface ProfileRepository {
    /** 키 사전순 — 렌더 바이트-안정의 전제. */
    fun observeEntries(): Flow<List<ProfileEntry>>
    suspend fun getEntries(): AppResult<List<ProfileEntry>>
    suspend fun upsert(key: String, value: String, source: String = ProfileEntry.SOURCE_MANUAL): AppResult<Unit>
    suspend fun delete(key: String): AppResult<Unit>
}
