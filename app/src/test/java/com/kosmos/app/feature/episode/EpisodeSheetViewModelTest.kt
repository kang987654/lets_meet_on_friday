package com.kosmos.app.feature.episode

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [EpisodeSheetViewModelTest]
 * 시트가 "불러오는 중"에 갇히지 않는 것과, 목록 새로고침 콜백이 성공 뒤에만 불리는 것을 고정합니다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeSheetViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository: EpisodeRepository = mockk()
    private lateinit var viewModel: EpisodeSheetViewModel

    private val episode = Episode(
        id = "e1", sessionId = "s1", status = EpisodeStatus.SUMMARIZED, title = "자전거", summary = "요약",
        tags = listOf("자전거"), startAt = 0, endAt = 10, messageCount = 2, retryCount = 0,
        createdAt = 0, updatedAt = 0
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = EpisodeSheetViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `없는 id 는 Missing 으로 끝난다`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getById("gone") } returns AppResult.Success(null)

        viewModel.load("gone")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(EpisodeSheetState.Missing, viewModel.state.value)
    }

    @Test
    fun `조회 실패는 Error 로 끝난다`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getById("e1") } returns AppResult.Failure(AppError.DbReadError("episode"))

        viewModel.load("e1")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value is EpisodeSheetState.Error)
    }

    @Test
    fun `삭제 성공이면 콜백, 실패면 안내만 남는다`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getById("e1") } returns AppResult.Success(episode)
        viewModel.load("e1")
        dispatcher.scheduler.advanceUntilIdle()

        coEvery { repository.delete("e1") } returns AppResult.Failure(AppError.DbWriteError("episode"))
        var changed = false
        viewModel.delete { changed = true }
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(changed)
        assertNotNull(viewModel.actionError.value)

        coEvery { repository.delete("e1") } returns AppResult.Success(Unit)
        viewModel.delete { changed = true }
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(changed)
        assertEquals(EpisodeSheetState.Missing, viewModel.state.value)
    }

    @Test
    fun `수정 성공이면 새 값이 보이고 콜백이 불린다`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getById("e1") } returns AppResult.Success(episode)
        coEvery { repository.update(any()) } returns AppResult.Success(Unit)
        viewModel.load("e1")
        dispatcher.scheduler.advanceUntilIdle()

        var changed = false
        viewModel.save("자전거 자물쇠", "자전거, 자물쇠", "", now = 99) { changed = true }
        dispatcher.scheduler.advanceUntilIdle()

        val loaded = viewModel.state.value as EpisodeSheetState.Loaded
        assertEquals("자전거 자물쇠", loaded.episode.title)
        assertEquals(listOf("자전거", "자물쇠"), loaded.episode.tags)
        assertEquals(99L, loaded.episode.updatedAt)
        assertTrue(changed)
    }
}
