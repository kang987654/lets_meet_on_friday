package com.kosmos.app.domain.cleanup

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelTurn
import com.kosmos.app.domain.usecase.SummarizeEpisodeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * [EpisodeMergeUseCasesTest]
 * 기억 정리 2차(0.31.0) — 후보 규칙(exp44), 연속 "같음" 묶음, 재요약 먼저·1편일 때만 DB 변경을 고정합니다.
 */
class EpisodeMergeUseCasesTest {

    private val h = 60L * 60 * 1000

    private fun ep(id: String, start: Long, title: String, tags: List<String>) = Episode(
        id = id, sessionId = "s", status = EpisodeStatus.SUMMARIZED, title = title, summary = "요약 $title",
        tags = tags, startAt = start, endAt = start + h / 2, messageCount = 2, retryCount = 0,
        createdAt = start, updatedAt = start
    )

    @Test
    fun `후보 규칙은 24시간 안의 겹침 또는 공유 태그 둘 이상이다`() {
        val dentist = ep("a", 0, "치과 예약 일정 추가", listOf("치과", "예약", "일정"))
        assertTrue(PlanEpisodeMergeUseCase.isCandidate(dentist, ep("b", h, "치과 예약 시간 변경", listOf("치과", "예약", "변경"))))
        // 제목은 안 겹치지만 태그 둘 공유 — exp44 의 러닝 쌍
        assertTrue(PlanEpisodeMergeUseCase.isCandidate(
            ep("c", 0, "주말 러닝 계획", listOf("러닝", "한강", "주말")),
            ep("d", h, "준비물 정리", listOf("러닝", "운동화", "한강"))
        ))
        assertFalse("다른 주제", PlanEpisodeMergeUseCase.isCandidate(dentist, ep("e", h, "점심 메뉴 추천", listOf("점심", "국밥"))))
        assertFalse("24시간 넘게 떨어짐", PlanEpisodeMergeUseCase.isCandidate(dentist, ep("f", 30 * h, "치과 예약 시간 변경", listOf("치과", "예약"))))
    }

    private val episodes: EpisodeRepository = mockk(relaxed = true)
    private val prompts = mutableListOf<String>()
    private fun model(same: (String) -> Boolean): ModelRunner = mockk {
        val p = slot<ChatPrompt>()
        coEvery { generate(capture(p), any()) } answers {
            prompts += p.captured.currentInput
            AppResult.Success(ModelTurn(if (same(p.captured.currentInput)) "판정: 같음" else "판정: 다름"))
        }
    }

    @Test
    fun `연속한 같음은 한 묶음이 되고 후보가 아닌 쌍은 모델에 보내지 않는다`() = runTest {
        coEvery { episodes.getByStatus(EpisodeStatus.SUMMARIZED) } returns AppResult.Success(
            listOf(
                ep("m3", 2 * h, "회의 참석자 알림", listOf("회의", "참석자", "월요일")),
                ep("m1", 0, "회의 안건 정리", listOf("회의", "안건", "월요일")),
                ep("m2", h, "회의실 예약", listOf("회의실", "회의", "월요일")),
                ep("x", 3 * h, "점심 메뉴 추천", listOf("점심", "국밥"))
            )
        )

        val proposals = (PlanEpisodeMergeUseCase(episodes, model { true })(ZoneId.of("UTC")) as AppResult.Success).data

        assertEquals(listOf("m1+m2+m3"), proposals.map { it.key })
        assertEquals("후보 2쌍만 판정", 2, prompts.size)
        assertTrue(prompts.none { "점심" in it })
        assertTrue(prompts.first().startsWith("대화 A: 1월 1일 0시 · 제목: 회의 안건 정리"))
    }

    @Test
    fun `다름이면 제안하지 않는다`() = runTest {
        coEvery { episodes.getByStatus(EpisodeStatus.SUMMARIZED) } returns AppResult.Success(
            listOf(ep("a", 0, "치과 예약 일정 추가", listOf("치과", "예약")), ep("b", h, "치과 예약 시간 변경", listOf("치과", "예약")))
        )

        val proposals = (PlanEpisodeMergeUseCase(episodes, model { false })(ZoneId.of("UTC")) as AppResult.Success).data

        assertTrue(proposals.isEmpty())
    }

    // --- 적용 ---

    private val conversations: ConversationRepository = mockk()
    private val summarize: SummarizeEpisodeUseCase = mockk()
    private val audit: AuditTrailService = mockk(relaxed = true)

    private fun msg(id: String, at: Long, episodeId: String) =
        ChatMessage(id, "s", ChatMessage.Role.USER, "내용 $id", InputType.TEXT, createdAt = at, episodeId = episodeId)

    private val a = ep("a", 0, "치과 예약 일정 추가", listOf("치과"))
    private val b = ep("b", h, "치과 예약 시간 변경", listOf("치과"))

    private fun givenMessages() {
        coEvery { conversations.getByEpisode("a") } returns AppResult.Success(listOf(msg("1", 1, "a")))
        coEvery { conversations.getByEpisode("b") } returns AppResult.Success(listOf(msg("2", h + 1, "b")))
    }

    @Test
    fun `재요약이 두 편이면 DB 를 건드리지 않는다`() = runTest {
        givenMessages()
        coEvery { summarize(any()) } returns AppResult.Success(
            listOf(SummarizeEpisodeUseCase.EpisodeDoc("치과", listOf("치과"), "요약"), SummarizeEpisodeUseCase.EpisodeDoc("점심", listOf("점심"), "요약"))
        )

        val applied = ApplyEpisodeMergeUseCase(conversations, episodes, summarize, audit)(listOf(EpisodeMergeProposal(listOf(a, b))))

        assertEquals(0, applied)
        coVerify(exactly = 0) { episodes.mergeInto(any(), any()) }
    }

    @Test
    fun `한 편이면 이은 메시지로 요약하고 첫 에피소드로 합친다`() = runTest {
        givenMessages()
        val input = slot<List<ChatMessage>>()
        coEvery { summarize(capture(input)) } returns AppResult.Success(
            listOf(SummarizeEpisodeUseCase.EpisodeDoc("미소치과 예약 변경", listOf("치과", "예약"), "합친 요약"))
        )
        val merged = slot<Episode>()
        coEvery { episodes.mergeInto("b", capture(merged)) } returns AppResult.Success(Unit)

        val applied = ApplyEpisodeMergeUseCase(conversations, episodes, summarize, audit)(listOf(EpisodeMergeProposal(listOf(a, b))), now = 99L)

        assertEquals(1, applied)
        assertEquals(listOf("1", "2"), input.captured.map { it.id })
        assertEquals("a", merged.captured.id)
        assertEquals("미소치과 예약 변경", merged.captured.title)
        assertEquals(b.endAt, merged.captured.endAt)
        assertEquals(2, merged.captured.messageCount)
        coVerify(exactly = 1) { audit.logToolCall(any(), "EpisodeMerge", any(), any()) }
    }
}
