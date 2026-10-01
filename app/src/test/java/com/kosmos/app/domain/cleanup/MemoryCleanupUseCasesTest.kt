package com.kosmos.app.domain.cleanup

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelTurn
import com.kosmos.app.domain.tool.Tokenizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * [MemoryCleanupUseCasesTest]
 * 수동 기억 정리(0.30.0) 도메인 — 병합 가드(exp42 함정), 판정→제안, 적용 순서, 주간 회고 창·교체를 고정합니다.
 */
class MemoryCleanupUseCasesTest {

    private fun note(id: String, content: String, source: String = KnowledgeNote.SOURCE_MANUAL, createdAt: Long = 0L) =
        KnowledgeNote(id = id, content = content, createdAt = createdAt, updatedAt = createdAt, source = source)

    // --- 가드 ---

    @Test
    fun `가드는 숫자가 다르거나 대상이 다른 함정을 후보에서 뺀다`() {
        val cases = listOf(
            Triple("자전거 자물쇠 비밀번호는 4821", "자전거 자물쇠 번호 4821", true),
            Triple("여동생 생일은 5월 3일", "여동생 생일 5월 3일", true),
            Triple("땅콩 알레르기가 있다", "사용자는 땅콩 알레르기 있음", true),
            Triple("자전거 자물쇠 비밀번호는 4821", "자전거 자물쇠 비밀번호는 4812", false), // 숫자 다름
            Triple("자전거 자물쇠 번호 4821", "현관 비밀번호는 4821", false),               // 대상 다름 (exp42 오병합)
            Triple("강아지 이름은 콩이", "운동화 사이즈 270", false),
        )
        cases.forEach { (a, b, expected) -> assertEquals("$a | $b", expected, MemoryMergeGuard.isCandidate(a, b)) }
    }

    @Test
    fun `합친 문장이 원문 숫자를 잃으면 실패로 본다`() {
        assertTrue(MemoryMergeGuard.keepsNumbers("자전거 자물쇠 비밀번호는 4821이다", listOf("자물쇠 4821", "자전거 자물쇠 번호 4821")))
        assertFalse(MemoryMergeGuard.keepsNumbers("자전거 자물쇠 비밀번호다", listOf("자물쇠 4821")))
    }

    // --- 계획 ---

    private val repo: KnowledgeRepository = mockk(relaxed = true)
    private val prompts = mutableListOf<String>()

    private fun model(reply: (String) -> String): ModelRunner = mockk {
        val p = slot<ChatPrompt>()
        coEvery { generate(capture(p), any()) } answers {
            prompts += p.captured.currentInput
            AppResult.Success(ModelTurn(reply(p.captured.currentInput)))
        }
    }

    @Test
    fun `같음 판정만 제안이 되고 후보가 아닌 쌍은 모델에 보내지 않는다`() = runTest {
        coEvery { repo.getNotes(any(), any()) } returns AppResult.Success(
            listOf(
                note("a", "와이파이 비밀번호는 kosmos123"),
                note("b", "집 와이파이 비번 kosmos123"),
                note("c", "땅콩 알레르기가 있다"),
                note("d", "땅콩버터를 좋아한다"),
                note("e", "운동화 사이즈 270")
            )
        )
        val runner = model { input ->
            if ("와이파이" in input) "판정: 같음\n합친 문장: 집 와이파이 비밀번호는 kosmos123이다." else "판정: 다름\n합친 문장: 없음"
        }

        val proposals = (PlanMemoryMergeUseCase(repo, runner)() as AppResult.Success).data

        assertEquals(listOf("a+b"), proposals.map { it.key })
        assertEquals("집 와이파이 비밀번호는 kosmos123이다.", proposals.single().mergedContent)
        assertTrue("운동화는 어떤 후보에도 들지 않는다", prompts.none { "운동화" in it })
        assertTrue(prompts.all { it.startsWith("기억들:\n- ") })
    }

    @Test
    fun `숫자를 잃은 합친 문장은 제안하지 않는다`() = runTest {
        coEvery { repo.getNotes(any(), any()) } returns AppResult.Success(
            listOf(note("a", "자전거 자물쇠 비밀번호는 4821"), note("b", "자전거 자물쇠 번호 4821"))
        )
        val runner = model { "판정: 같음\n합친 문장: 자전거 자물쇠 비밀번호" }

        val proposals = (PlanMemoryMergeUseCase(repo, runner)() as AppResult.Success).data

        assertTrue(proposals.isEmpty())
    }

    @Test
    fun `세 개가 이어지면 묶음 전체로 한 번 더 판정한다`() = runTest {
        coEvery { repo.getNotes(any(), any()) } returns AppResult.Success(
            listOf(
                note("a", "자전거 자물쇠 비밀번호는 4821"),
                note("b", "자전거 자물쇠 번호 4821"),
                note("c", "내 자전거 자물쇠 비번은 4821이야")
            )
        )
        val runner = model { "판정: 같음\n합친 문장: 자전거 자물쇠 비밀번호는 4821이다." }

        val proposals = (PlanMemoryMergeUseCase(repo, runner)() as AppResult.Success).data

        assertEquals(listOf("a+b+c"), proposals.map { it.key })
        assertEquals("쌍 3번 + 묶음 1번", 4, prompts.size)
        assertEquals(3, prompts.last().lines().count { it.startsWith("- ") })
    }

    // --- 적용 ---

    private val audit: AuditTrailService = mockk(relaxed = true)

    @Test
    fun `저장이 실패하면 원본을 지우지 않는다`() = runTest {
        coEvery { repo.save(any()) } returns AppResult.Failure(AppError.DbWriteError("knowledge"))
        val proposal = MergeProposal(listOf(note("a", "x 1"), note("b", "x 1")), "x 1")

        val applied = ApplyMemoryMergeUseCase(repo, audit)(listOf(proposal))

        assertEquals(0, applied)
        coVerify(exactly = 0) { repo.delete(any()) }
    }

    @Test
    fun `적용하면 합친 노트를 저장하고 원본을 지우며 수동이 섞이면 수동으로 남긴다`() = runTest {
        val saved = slot<KnowledgeNote>()
        coEvery { repo.save(capture(saved)) } returns AppResult.Success(Unit)
        val proposal = MergeProposal(
            listOf(note("a", "x 1", KnowledgeNote.SOURCE_AUTO, createdAt = 50L), note("b", "x 1", KnowledgeNote.SOURCE_MANUAL, createdAt = 10L)),
            "합친 x 1"
        )
        // 같은 노트(a)가 겹치는 두 번째 제안은 건너뛴다.
        val overlapping = MergeProposal(listOf(note("a", "x 1"), note("c", "x 1")), "다른 합침 x 1")

        val applied = ApplyMemoryMergeUseCase(repo, audit)(listOf(proposal, overlapping), now = 99L)

        assertEquals(1, applied)
        assertEquals("합친 x 1", saved.captured.content)
        assertEquals(KnowledgeNote.SOURCE_MANUAL, saved.captured.source)
        assertEquals(10L, saved.captured.createdAt)
        coVerify(exactly = 1) { repo.delete("a") }
        coVerify(exactly = 1) { repo.delete("b") }
        coVerify(exactly = 0) { repo.delete("c") }
        coVerify(exactly = 1) { audit.logToolCall(any(), "MemoryMerge", any(), any()) }
    }

    // --- 주간 회고 ---

    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-01T12:00:00Z").toEpochMilli()
    private val day = 24L * 60 * 60 * 1000
    private val episodes: EpisodeRepository = mockk()
    private val tokenizer = object : Tokenizer {
        override fun sizeInTokens(text: String): Int = text.length
    }

    private fun episode(id: String, startAt: Long, summary: String?) = Episode(
        id = id, sessionId = "s", status = EpisodeStatus.SUMMARIZED, title = "제목$id", summary = summary,
        tags = emptyList(), startAt = startAt, endAt = startAt, messageCount = 2, retryCount = 0,
        createdAt = startAt, updatedAt = startAt
    )

    @Test
    fun `7일 안 에피소드가 없으면 모델을 부르지 않는다`() = runTest {
        coEvery { episodes.getByStatus(EpisodeStatus.SUMMARIZED) } returns AppResult.Success(
            listOf(episode("old", now - 8 * day, "오래된 일"))
        )
        val runner = model { "회고" }

        val result = GenerateWeeklyReviewUseCase(episodes, repo, runner, tokenizer)(now, utc)

        assertNull((result as AppResult.Success).data)
        assertTrue(prompts.isEmpty())
    }

    @Test
    fun `회고는 7일 안 에피소드를 시간순으로 넣고 주차 키로 저장한다`() = runTest {
        coEvery { episodes.getByStatus(EpisodeStatus.SUMMARIZED) } returns AppResult.Success(
            listOf(
                episode("b", now - 1 * day, "러닝 계획"),
                episode("a", now - 3 * day, "치과 예약"),
                episode("old", now - 9 * day, "범위 밖"),
                episode("empty", now - 2 * day, null)
            )
        )
        coEvery { repo.save(any()) } returns AppResult.Success(Unit)
        val runner = model { "이번 주는 치과와 러닝 준비에 시간을 썼습니다." }

        val note = (GenerateWeeklyReviewUseCase(episodes, repo, runner, tokenizer)(now, utc) as AppResult.Success).data

        val input = prompts.single()
        assertTrue(input.indexOf("치과 예약") < input.indexOf("러닝 계획"))
        assertFalse("범위 밖" in input)
        assertEquals("weekly-review-2026-W40", note?.id)
        assertEquals(listOf(GenerateWeeklyReviewUseCase.TAG, "2026-W40"), note?.tags)
        assertEquals(KnowledgeNote.SOURCE_AUTO, note?.source)
    }
}
