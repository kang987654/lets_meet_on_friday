package com.kosmos.app.assistant.episode

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.modelrunner.ConversationResetEvent
import com.kosmos.app.domain.modelrunner.ModelRunner
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EpisodeBoundaryManagerTest]
 * 에피소드 경계 판정(무활동 30분 + 예산 리셋)을 검증합니다 (ADR-022).
 *
 * [WHY] 경계가 틀리는 두 방향의 대가가 다르다 — 과분절은 검색 문서가 잘게 늘어나는 것으로
 * 끝나지만, 미분절은 에피소드가 한없이 자라 요약 입력 상한에 걸리고 아카이브가 비게 된다.
 * 리셋 사유 필터(TOKEN_BUDGET 만)는 "웹 검색 토글이 주제를 가르는" 오분절을 막는 계약이다.
 */
class EpisodeBoundaryManagerTest {

    private val episodeRepository: EpisodeRepository = mockk(relaxed = true)
    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val resets = MutableSharedFlow<ConversationResetEvent>(extraBufferCapacity = 4)
    private val modelRunner: ModelRunner = mockk(relaxed = true) {
        io.mockk.every { conversationResets } returns (resets as SharedFlow<ConversationResetEvent>)
    }

    private val savedEpisodes = mutableListOf<Episode>()

    private fun manager(): EpisodeBoundaryManager {
        coEvery { episodeRepository.insert(any()) } answers {
            savedEpisodes += firstArg<Episode>(); AppResult.Success(Unit)
        }
        coEvery { episodeRepository.update(any()) } answers {
            savedEpisodes += firstArg<Episode>(); AppResult.Success(Unit)
        }
        return EpisodeBoundaryManager(episodeRepository, conversationRepository, modelRunner)
    }

    private fun openEpisode(id: String = "e-open", startAt: Long = 0) = Episode(
        id = id, sessionId = "s1", status = EpisodeStatus.OPEN, title = null, summary = null,
        tags = emptyList(), startAt = startAt, endAt = null, messageCount = 0, retryCount = 0,
        createdAt = startAt, updatedAt = startAt
    )

    private fun message(at: Long) = ChatMessage(
        id = "m$at", sessionId = "s1", role = ChatMessage.Role.USER, content = "x",
        inputType = InputType.TEXT, createdAt = at
    )

    private fun stubOpen(episode: Episode?) {
        coEvery { episodeRepository.getByStatus(EpisodeStatus.OPEN) } returns
            AppResult.Success(listOfNotNull(episode))
    }

    private fun stubLastMessage(at: Long?) {
        coEvery { conversationRepository.getRecentBySession("s1", 1) } returns
            AppResult.Success(listOfNotNull(at?.let { message(it) }))
    }

    @Test
    fun `열린 에피소드가 없으면 새로 열고 그 id 를 배정한다`() = runBlocking {
        stubOpen(null)
        stubLastMessage(null)

        val id = manager().onUserMessage("s1", now = 1_000L)

        assertEquals(savedEpisodes.single().id, id)
        assertEquals(EpisodeStatus.OPEN, savedEpisodes.single().status)
    }

    @Test
    fun `30분 이내면 기존 에피소드를 그대로 쓴다`() = runBlocking {
        stubOpen(openEpisode("e1"))
        stubLastMessage(at = 100_000L)

        val id = manager().onUserMessage("s1", now = 100_000L + 29 * 60_000L)

        assertEquals("e1", id)
        assertTrue("경계가 아니면 아무것도 저장하지 않는다", savedEpisodes.isEmpty())
    }

    @Test
    fun `30분 초과면 닫고 새 에피소드를 연다`() = runBlocking {
        stubOpen(openEpisode("e1"))
        stubLastMessage(at = 100_000L)
        coEvery { conversationRepository.getByEpisode("e1") } returns
            AppResult.Success(listOf(message(90_000L), message(100_000L)))

        val id = manager().onUserMessage("s1", now = 100_000L + 31 * 60_000L)

        val closed = savedEpisodes.first { it.id == "e1" }
        assertEquals(EpisodeStatus.CLOSED, closed.status)
        // [WHY] endAt 은 판정 시각이 아니라 마지막 메시지 시각 — 30분 공백은 에피소드가 아니다.
        assertEquals(100_000L, closed.endAt)
        assertEquals(2, closed.messageCount)
        assertNotEquals("새 에피소드가 열려야 한다", "e1", id)
    }

    @Test
    fun `TOKEN_BUDGET 리셋은 에피소드를 닫는다`() = runBlocking {
        stubOpen(openEpisode("e1"))
        stubLastMessage(at = 100_000L)
        coEvery { conversationRepository.getByEpisode("e1") } returns
            AppResult.Success(listOf(message(100_000L)))
        val m = manager()

        resets.emit(ConversationResetEvent("s1", ConversationResetEvent.Reason.TOKEN_BUDGET))
        waitUntil { savedEpisodes.any { it.id == "e1" && it.status == EpisodeStatus.CLOSED } }

        assertTrue(savedEpisodes.any { it.id == "e1" && it.status == EpisodeStatus.CLOSED })
    }

    @Test
    fun `TOOLS·SYSTEM_INSTRUCTION 리셋은 경계가 아니다`() = runBlocking {
        // [WHY] 웹 검색 토글이나 응답 스타일 변경이 주제를 가르면 안 된다 (ADR-022).
        stubOpen(openEpisode("e1"))
        stubLastMessage(at = 100_000L)
        val m = manager()

        resets.emit(ConversationResetEvent("s1", ConversationResetEvent.Reason.TOOLS))
        resets.emit(ConversationResetEvent("s1", ConversationResetEvent.Reason.SYSTEM_INSTRUCTION))
        delay(200)

        assertTrue("설정 변경 리셋에 에피소드가 닫혔다", savedEpisodes.isEmpty())
    }

    @Test
    fun `저장소 실패 시 null 을 돌려 채팅을 막지 않는다`() = runBlocking {
        coEvery { episodeRepository.getByStatus(any()) } throws RuntimeException("db down")

        val id = manager().onUserMessage("s1", now = 1_000L)

        assertEquals(null, id)
    }

    private suspend fun waitUntil(timeoutMs: Long = 2_000, cond: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!cond() && System.currentTimeMillis() - start < timeoutMs) delay(20)
    }
}
