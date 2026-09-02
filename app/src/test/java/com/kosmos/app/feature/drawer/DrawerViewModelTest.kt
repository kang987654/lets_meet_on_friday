package com.kosmos.app.feature.drawer

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.memory.ProfileRepository
import com.kosmos.app.domain.memory.ProfileSuggestionRepository
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * [DrawerViewModelTest]
 * 드로어 검색이 `SearchMemory` 와 같은 3단(정밀 → 바이그램 정렬 → 스캔+임계)을 따르는지 고정합니다 (C1 M3).
 *
 * [WHY] 예전에는 LIKE 두 결과를 정렬 없이 이어 붙였다 — 드로어에서 보이는 순서와 모델이 회수하는
 * 순서가 달랐다(ui_a_prime.md 동작 규칙 위반).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DrawerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val episodes: EpisodeRepository = mockk()
    private val profiles: ProfileRepository = mockk { every { observeEntries() } returns flowOf(emptyList()) }
    private val suggestions: ProfileSuggestionRepository = mockk { every { observePending() } returns flowOf(emptyList()) }

    private fun viewModel() = DrawerViewModel(episodes, profiles, suggestions)

    private fun episode(id: String, title: String, summary: String, tags: List<String>, createdAt: Long) = Episode(
        id = id, sessionId = "s", status = EpisodeStatus.SUMMARIZED, title = title, summary = summary, tags = tags,
        startAt = 1, endAt = 2, messageCount = 2, retryCount = 0, createdAt = createdAt, updatedAt = createdAt
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { episodes.search(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { episodes.searchByTags(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { episodes.getEpisodes(any(), any()) } returns AppResult.Success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `빈 질의는 null 이다`() = runTest(dispatcher.scheduler) {
        assertNull(viewModel().search("   "))
    }

    @Test
    fun `정밀 후보는 바이그램 점수순으로 정렬된다`() = runTest(dispatcher.scheduler) {
        val exact = episode("e1", "자전거 비밀번호 변경", "4321로 바꿨다", listOf("자전거", "비밀번호"), createdAt = 1)
        val partial = episode("e2", "현관 비밀번호", "5678", listOf("비밀번호"), createdAt = 9)
        coEvery { episodes.search("자전거 비밀번호", any()) } returns AppResult.Success(listOf(partial, exact))

        val result = viewModel().search("자전거 비밀번호")

        assertEquals(listOf("e1", "e2"), result!!.map { it.id })
    }

    @Test
    fun `정밀 0건이면 스캔이 바꿔 말하기를 건지고 임계 미달은 버린다`() = runTest(dispatcher.scheduler) {
        val lock = episode("e1", "자전거 비밀번호 변경", "자물쇠 비밀번호를 4321로", listOf("자전거", "비밀번호"), createdAt = 1)
        val noise = episode("e2", "커피 추천", "라떼를 추천했다", listOf("커피"), createdAt = 2)
        coEvery { episodes.getEpisodes(any(), any()) } returns AppResult.Success(listOf(noise, lock))

        val result = viewModel().search("자물쇠번호")

        assertEquals(listOf("e1"), result!!.map { it.id })
    }
}
