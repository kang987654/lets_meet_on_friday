package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.tool.Tokenizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [SearchMemoryToolExecutorTest]
 * 0.25.0 의 3단 검색(정밀 LIKE → 바이그램 전수 스캔 → 태그 목록)을 고정합니다.
 * 기존 계약은 [com.kosmos.app.integration.MemoryPipelineIntegrationTest] 가 그대로 지키고,
 * 여기서는 **새로 생긴 2단(스캔)의 경계**만 본다.
 */
// [WHY] Robolectric — 실행기가 org.json.JSONObject 로 봉투를 조립한다(순수 JVM 에서는 "not mocked").
@RunWith(RobolectricTestRunner::class)
class SearchMemoryToolExecutorTest {

    private val repository: KnowledgeRepository = mockk()
    private val episodes: EpisodeRepository = mockk()
    private val tokenizer: Tokenizer = mockk {
        every { sizeInTokens(any()) } answers { firstArg<String>().length / 3 }
    }

    private fun executor() = SearchMemoryToolExecutor(repository, episodes, tokenizer)

    private fun note(id: String, content: String, tags: List<String> = emptyList(), createdAt: Long = 1) =
        KnowledgeNote(id = id, content = content, tags = tags, createdAt = createdAt, updatedAt = createdAt)

    private fun episode(id: String, title: String, summary: String, tags: List<String>, createdAt: Long = 1) = Episode(
        id = id, sessionId = "s", status = EpisodeStatus.SUMMARIZED, title = title, summary = summary, tags = tags,
        startAt = 1, endAt = 2, messageCount = 2, retryCount = 0, createdAt = createdAt, updatedAt = createdAt
    )

    @Before
    fun preciseMiss() {
        // 기본: 정밀 단계는 전부 0건 — 각 테스트가 스캔 재료만 바꾼다.
        coEvery { repository.search(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { repository.searchByTags(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { episodes.search(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { episodes.searchByTags(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { episodes.getEpisodes(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { repository.searchRecent(any()) } returns AppResult.Success(emptyList())
    }

    private suspend fun run(keyword: String) =
        executor().execute(ToolArguments.of(mapOf("keyword" to keyword)), "s1")

    @Test
    fun `정밀 적중이면 전수 스캔을 하지 않는다`() = runBlocking {
        coEvery { repository.search("자전거", any()) } returns AppResult.Success(listOf(note("k1", "자전거 비밀번호는 4936")))

        val json = run("자전거")

        assertTrue(json.contains("\"status\":\"success\"") && json.contains("4936"))
        coVerify(exactly = 0) { repository.searchRecent(any()) }
    }

    @Test
    fun `정밀 0건이면 붙여쓰기 변형을 바이그램 스캔으로 건진다`() = runBlocking {
        // LIKE '%자물쇠번호%' 는 "자물쇠 비밀번호" 를 못 맞히지만 바이그램 겹침은 0.75 다.
        coEvery { repository.searchRecent(any()) } returns AppResult.Success(
            listOf(note("k1", "자전거 자물쇠 비밀번호는 4936", listOf("비밀번호")), note("k2", "회식은 마지막 금요일"))
        )

        val json = run("자물쇠번호")

        assertTrue(json, json.contains("\"status\":\"success\"") && json.contains("4936"))
        assertTrue("무관한 문서는 임계에 걸려야 한다", !json.contains("회식"))
    }

    @Test
    fun `스캔에서 건진 에피소드는 회수 칩 meta 를 동봉한다`() = runBlocking {
        coEvery { episodes.getEpisodes(any(), any()) } returns AppResult.Success(
            listOf(episode("e7", "자전거 비밀번호 변경", "비밀번호를 4321로 바꿨다", listOf("자전거", "비밀번호")))
        )

        val json = run("자전거비번")

        assertTrue(json, json.contains("\"episodeIds\":[\"e7\"]"))
        assertTrue(json.contains("(과거 대화)"))
    }

    @Test
    fun `임계 미달이면 태그 목록 폴백으로 떨어진다`() = runBlocking {
        // MemoryPipelineIntegrationTest 와 같은 재료 — 바이그램이 이 계약을 깨지 않아야 한다.
        coEvery { repository.searchRecent(any()) } returns AppResult.Success(
            listOf(note("k1", "커피보다 녹차를 더 좋아함", listOf("선호도", "음료")))
        )

        val json = run("좋아하는 것")

        assertTrue(json.contains("선호도") && json.contains("한 번 더 호출") && json.contains("지어내지 마세요"))
        assertTrue("스캔 결과로 오인되면 안 된다", !json.contains("\"meta\""))
    }

    @Test
    fun `스캔 재료 조회 실패는 오류로 되돌린다`() = runBlocking {
        coEvery { repository.searchRecent(any()) } returns AppResult.Failure(AppError.DbReadError("knowledge_note"))

        val json = run("자물쇠번호")

        assertTrue(json.contains("\"status\":\"error\""))
    }

    @Test
    fun `정밀 후보의 순위는 바이그램 점수다`() = runBlocking {
        // "비밀번호" 한 항만 맞는 문서보다, 두 항이 온전히 든 문서가 앞선다(2.0 > 1.0). 오래된 쪽이 더 잘 맞아도 앞선다.
        val both = note("k-both", "자전거 비밀번호는 1234", createdAt = 1)
        val one = note("k-one", "현관 비밀번호는 5678", createdAt = 999)
        coEvery { repository.search("자전거", any()) } returns AppResult.Success(listOf(both))
        coEvery { repository.search("비밀번호", any()) } returns AppResult.Success(listOf(one, both))

        val json = run("자전거 비밀번호")

        assertTrue(json.indexOf("1234") < json.indexOf("5678"))
    }
}
