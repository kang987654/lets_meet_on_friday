package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.ProfileSuggestion
import com.kosmos.app.domain.model.ProfileSuggestionStatus
import kotlinx.coroutines.flow.Flow

/**
 * 프로필 제안 저장소 (C′2) — 자동 추출이 쓰고, 채팅 승인 카드가 읽어 승인/거절로 종결한다.
 */
interface ProfileSuggestionRepository {
    /** PENDING 만, 오래된 것부터 — 카드는 첫 항목을 보이고 나머지는 "외 N건". */
    fun observePending(): Flow<List<ProfileSuggestion>>
    suspend fun insert(suggestion: ProfileSuggestion): AppResult<Unit>
    suspend fun updateStatus(id: String, status: ProfileSuggestionStatus, updatedAt: Long): AppResult<Unit>

    /**
     * 같은 키-값 제안이 **어느 상태로든** 있으면 true.
     *
     * [WHY] 거절한 사실을 다음 에피소드가 다시 제안하면 잔소리가 된다 — 상태를 가리지 않고
     * 막는다. 값이 달라진 같은 키(예: 이사)는 새 제안으로 통과한다.
     */
    suspend fun exists(key: String, value: String): Boolean
}
