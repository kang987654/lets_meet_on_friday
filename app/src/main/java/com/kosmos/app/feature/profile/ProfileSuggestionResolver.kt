package com.kosmos.app.feature.profile

import com.kosmos.app.assistant.context.projectedProfileTokens
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.memory.ProfileRepository
import com.kosmos.app.domain.memory.ProfileSuggestionRepository
import com.kosmos.app.domain.model.ProfileEntry
import com.kosmos.app.domain.model.ProfileSuggestion
import com.kosmos.app.domain.model.ProfileSuggestionStatus
import com.kosmos.app.domain.tool.Tokenizer
import javax.inject.Inject

/**
 * [ProfileSuggestionResolver]
 * 프로필 제안의 승인/거절을 종결합니다 (C′2) — 채팅 카드와 프로필 시트가 공유한다.
 *
 * ### Architecture Context
 * - **Layer**: Feature (Profile)
 * - **Dependencies**: [ProfileRepository], [ProfileSuggestionRepository], [Tokenizer]
 *
 * [WHY] 승인 경로도 **상한 100토큰을 집행**한다 — [ProfileSheetViewModel] 과 같은 투영 검사
 * ([projectedProfileTokens]). 자동 경로가 이를 우회하면 `PREFILL_OVERHEAD >= 실측 + PROFILE_MAX_TOKENS`
 * 불변식이 깨져 매 턴 프리필이 예약을 넘는다.
 */
class ProfileSuggestionResolver @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val suggestionRepository: ProfileSuggestionRepository,
    private val tokenizer: Tokenizer
) {

    suspend fun accept(suggestion: ProfileSuggestion, now: Long = System.currentTimeMillis()): AppResult<Unit> {
        val current = (profileRepository.getEntries() as? AppResult.Success)?.data.orEmpty()
        val estimated = projectedProfileTokens(current, suggestion.key, suggestion.value, tokenizer)
        if (estimated > Constants.PROFILE_MAX_TOKENS) {
            return AppResult.Failure(
                AppError.ValidationError(
                    "profile",
                    "프로필이 너무 길어요(${estimated}/${Constants.PROFILE_MAX_TOKENS}토큰). 항목을 줄이면 저장할 수 있어요."
                )
            )
        }
        val upsert = profileRepository.upsert(suggestion.key, suggestion.value, ProfileEntry.SOURCE_AUTO)
        if (upsert is AppResult.Failure) return upsert
        return suggestionRepository.updateStatus(suggestion.id, ProfileSuggestionStatus.ACCEPTED, now)
    }

    /** 지금 프로필 — 제안 시트가 기존 값을 바꾸는지 보이게 한다. */
    fun observeProfile(): kotlinx.coroutines.flow.Flow<List<ProfileEntry>> = profileRepository.observeEntries()

    suspend fun reject(suggestion: ProfileSuggestion, now: Long = System.currentTimeMillis()): AppResult<Unit> =
        suggestionRepository.updateStatus(suggestion.id, ProfileSuggestionStatus.REJECTED, now)

    /**
     * 체크 목록 결과를 한 번에 종결한다 — [selectedIds] 는 차례로 승인(상한은 앞서 저장한 항목까지 누적해 검사), 나머지는 거절.
     * 상한을 넘거나 저장에 실패한 항목은 대기로 남는다(프로필을 줄이면 다시 저장할 수 있다).
     */
    suspend fun resolve(
        suggestions: List<ProfileSuggestion>,
        selectedIds: Set<String>,
        now: Long = System.currentTimeMillis()
    ): Resolution {
        val saved = mutableListOf<ProfileSuggestion>()
        var firstError: AppError? = null
        suggestions.forEach { s ->
            val result = if (s.id in selectedIds) accept(s, now) else reject(s, now)
            when {
                result is AppResult.Failure -> if (firstError == null) firstError = result.error
                s.id in selectedIds -> saved += s
            }
        }
        return Resolution(saved.toList(), firstError)
    }

    /** [resolve] 결과 — 저장한 제안과 첫 실패(없으면 null). */
    data class Resolution(val saved: List<ProfileSuggestion>, val error: AppError?)
}
