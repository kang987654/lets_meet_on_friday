package com.kosmos.app.feature.profile

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.ProfileRepository
import com.kosmos.app.domain.memory.ProfileSuggestionRepository
import com.kosmos.app.domain.model.ProfileEntry
import com.kosmos.app.domain.model.ProfileSuggestion
import com.kosmos.app.domain.model.ProfileSuggestionStatus
import com.kosmos.app.runtime.gemma.GemmaTokenizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ProfileSuggestionResolverTest]
 * 제안 승인이 **상한을 집행**하고 source=auto 로 저장하는지, 거절이 이력으로 남는지 고정합니다.
 * 추정기는 [ProfileSheetViewModelTest] 와 같은 실물 [GemmaTokenizer].
 */
class ProfileSuggestionResolverTest {

    private val profileRepository: ProfileRepository = mockk {
        coEvery { getEntries() } returns AppResult.Success(emptyList())
        coEvery { upsert(any(), any(), any()) } returns AppResult.Success(Unit)
    }
    private val suggestionRepository: ProfileSuggestionRepository = mockk {
        coEvery { updateStatus(any(), any(), any()) } returns AppResult.Success(Unit)
    }
    private val tokenizer = GemmaTokenizer()

    private fun resolver() = ProfileSuggestionResolver(profileRepository, suggestionRepository, tokenizer)

    private fun suggestion(key: String = "이름", value: String = "진우") = ProfileSuggestion(
        id = "s1", key = key, value = value, episodeId = "e1",
        status = ProfileSuggestionStatus.PENDING, createdAt = 1, updatedAt = 1
    )

    @Test
    fun `승인은 source auto 로 저장하고 ACCEPTED 로 종결한다`() = runBlocking {
        val result = resolver().accept(suggestion(), now = 77)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { profileRepository.upsert("이름", "진우", ProfileEntry.SOURCE_AUTO) }
        coVerify(exactly = 1) { suggestionRepository.updateStatus("s1", ProfileSuggestionStatus.ACCEPTED, 77) }
    }

    @Test
    fun `상한을 넘는 승인은 차단되고 제안은 대기로 남는다`() = runBlocking {
        // 한글 ≈ 1.2자/토큰 — 상한 100토큰을 확실히 넘는 300자 값.
        val result = resolver().accept(suggestion("자기소개", "가".repeat(300)))

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.ValidationError && error.reason.contains("토큰"))
        coVerify(exactly = 0) { profileRepository.upsert(any(), any(), any()) }
        coVerify(exactly = 0) { suggestionRepository.updateStatus(any(), any(), any()) }
    }

    @Test
    fun `상한 검사는 기존 프로필과 합산한다`() = runBlocking {
        // 기존 항목이 상한 근처를 차지하면 짧은 제안도 합계로 막혀야 한다.
        coEvery { profileRepository.getEntries() } returns AppResult.Success(
            listOf(ProfileEntry("자기소개", "가".repeat(280)))
        )

        val result = resolver().accept(suggestion())

        assertTrue(result is AppResult.Failure)
        coVerify(exactly = 0) { profileRepository.upsert(any(), any(), any()) }
    }

    @Test
    fun `프로필 저장 실패면 제안 상태를 바꾸지 않는다`() = runBlocking {
        coEvery { profileRepository.upsert(any(), any(), any()) } returns
            AppResult.Failure(AppError.DbWriteError("profile"))

        val result = resolver().accept(suggestion())

        assertTrue(result is AppResult.Failure)
        coVerify(exactly = 0) { suggestionRepository.updateStatus(any(), any(), any()) }
    }

    @Test
    fun `거절은 REJECTED 이력만 남긴다`() = runBlocking {
        resolver().reject(suggestion(), now = 9)

        coVerify(exactly = 1) { suggestionRepository.updateStatus("s1", ProfileSuggestionStatus.REJECTED, 9) }
        coVerify(exactly = 0) { profileRepository.upsert(any(), any(), any()) }
    }
}
