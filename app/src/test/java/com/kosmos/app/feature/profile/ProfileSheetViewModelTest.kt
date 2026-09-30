package com.kosmos.app.feature.profile

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.ProfileRepository
import com.kosmos.app.domain.model.ProfileEntry
import com.kosmos.app.runtime.gemma.GemmaTokenizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ProfileSheetViewModelTest]
 * 프로필 편집의 상한 집행을 고정합니다 — 상한 초과는 저장 자체가 차단돼야 예산 불변식이
 * 산다. 추정기는 실물 [GemmaTokenizer](과대 추정이 안전 방향, exp26 계수)를 쓴다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSheetViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val entriesFlow = MutableStateFlow<List<ProfileEntry>>(emptyList())
    private val repository: ProfileRepository = mockk {
        every { observeEntries() } returns entriesFlow
        coEvery { upsert(any(), any(), any()) } returns AppResult.Success(Unit)
        coEvery { delete(any()) } returns AppResult.Success(Unit)
    }
    private val tokenizer = GemmaTokenizer(mockk(), mockk())
    private lateinit var viewModel: ProfileSheetViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = ProfileSheetViewModel(repository, tokenizer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `정상 항목은 저장되고 onSaved 가 불린다`() = runTest(dispatcher.scheduler) {
        var saved = false

        viewModel.upsert("이름", "진우") { saved = true }
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.upsert("이름", "진우", ProfileEntry.SOURCE_MANUAL) }
        assertTrue(saved)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `상한을 넘는 저장은 차단되고 안내가 남는다`() = runTest(dispatcher.scheduler) {
        // 한글 ≈ 1.2자/토큰 — 상한 100토큰을 확실히 넘는 300자 값.
        val tooLong = "가".repeat(300)
        var saved = false

        viewModel.upsert("자기소개", tooLong) { saved = true }
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.upsert(any(), any(), any()) }
        assertTrue(!saved)
        assertNotNull(viewModel.error.value)
        assertTrue(viewModel.error.value!!.contains("토큰"))
    }

    @Test
    fun `빈 키·값은 조용히 무시한다`() = runTest(dispatcher.scheduler) {
        viewModel.upsert("  ", "값")
        viewModel.upsert("키", "  ")
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.upsert(any(), any(), any()) }
    }

    @Test
    fun `상한 초과 안내는 이후 성공한 삭제로 지워진다`() = runTest(dispatcher.scheduler) {
        viewModel.upsert("자기소개", "가".repeat(300))
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.error.value)

        viewModel.delete("이름")
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.error.value)
    }

    @Test
    fun `상한 초과 안내는 이후 성공한 저장으로 지워진다`() = runTest(dispatcher.scheduler) {
        viewModel.upsert("자기소개", "가".repeat(300))
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.error.value)

        viewModel.upsert("이름", "진우")
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.error.value)
    }

    @Test
    fun `빈 프로필의 토큰 사용량은 0이다`() = runTest(dispatcher.scheduler) {
        val collector = backgroundScope.launch { viewModel.tokenUsage.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.tokenUsage.value)

        entriesFlow.value = listOf(ProfileEntry(key = "이름", value = "진우"))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.tokenUsage.value > 0)
        collector.cancel()
    }

    @Test
    fun `삭제 실패는 안내로 남는다`() = runTest(dispatcher.scheduler) {
        coEvery { repository.delete("이름") } returns
            AppResult.Failure(AppError.DbWriteError("profile"))

        viewModel.delete("이름")
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.error.value)
    }
}
