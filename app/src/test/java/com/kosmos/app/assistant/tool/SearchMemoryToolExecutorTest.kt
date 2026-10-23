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
import org.junit.Assert.assertEquals
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

        // MemoryPipelineIntegrationTest 에서 옮긴 단언 — 폴백도 성공 응답이다(0.27.x 중복 정리).
        assertTrue(json.contains("\"status\":\"success\""))
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

        // MemoryPipelineIntegrationTest 에서 옮긴 단언 — 한 항만 맞는 노트도 빠지지 않는다.
        assertTrue("두 노트가 모두 있어야 한다", json.contains("1234") && json.contains("5678"))
        assertTrue(json.indexOf("1234") < json.indexOf("5678"))
    }

    @Test
    fun `동점이면 직접 저장한 메모가 더 최근의 긴 과거 대화 요약보다 살아남는다`() = runBlocking {
        // 실기기(2026-10-12) 재현: 요약 두 건이 질의 낱말을 다 담아 메모와 동점이고 더 최근이라, 절단에 메모가 잘렸다.
        val manual = note("k-manual", "자전거 비밀번호는 134", createdAt = 1)
        val longSummary = "사용자는 자전거 비밀번호를 여러 번 바꿨고 비서는 그 변경을 기억하겠다고 했으며 이후 일정 이야기도 길게 나눴다. ".repeat(12)
        val ep1 = episode("e1", "자전거 비밀번호 변경", "처음 1234 이후 4321. $longSummary", listOf("자전거", "비밀번호"), createdAt = 500)
        val ep2 = episode("e2", "자전거 비밀번호 요청", "4521 에서 4321 로. $longSummary", listOf("자전거", "비밀번호"), createdAt = 600)
        coEvery { repository.search(any(), any()) } returns AppResult.Success(listOf(manual))
        coEvery { episodes.search(any(), any()) } returns AppResult.Success(listOf(ep1, ep2))

        val json = run("자전거 비밀번호")

        assertTrue(json, json.contains("134"))
        // 0.36.1(사용자 확인 후 변경): 안내 문구에도 "(과거 대화)"가 들어 있어 줄 머리표까지 붙여 과거 대화 줄을 찾는다.
        assertTrue("메모가 과거 대화보다 앞에 실린다", json.indexOf("134") < json.indexOf("- (과거 대화)"))
    }

    @Test
    fun `메모와 과거 대화가 섞이면 메모를 믿으라는 안내가 맨 앞에 붙고 한쪽만이면 붙지 않는다`() = runBlocking {
        val manual = note("k-manual", "자전거 비밀번호는 134")
        val ep = episode("e1", "자전거 비밀번호 변경", "4321 로 바꿨다", listOf("자전거", "비밀번호"))
        coEvery { repository.search(any(), any()) } returns AppResult.Success(listOf(manual))
        coEvery { episodes.search(any(), any()) } returns AppResult.Success(listOf(ep))

        val mixed = run("자전거 비밀번호")
        assertTrue(mixed, mixed.contains("둘이 다르면 메모의 값을 답하고"))
        assertTrue("안내가 메모보다 앞", mixed.indexOf("둘이 다르면") < mixed.indexOf("134"))

        coEvery { episodes.search(any(), any()) } returns AppResult.Success(emptyList())
        val notesOnly = run("자전거 비밀번호")
        assertTrue(notesOnly, !notesOnly.contains("둘이 다르면"))
    }

    @Test
    fun `메모와 다른 값을 적은 요약은 한 줄로 줄이고 칩에서 빼며 메모 값을 적은 요약은 남긴다`() = runBlocking {
        // 실기기(2026-10-14): 4321 을 적은 요약이 여럿이면 안내가 있어도 4321 로 답했다(exp52) — exp53 조건 C.
        val manual = note("k-manual", "자전거 비밀번호는 134")
        // 결과 상한(MAX_KNOWLEDGE_CONTEXT_ITEMS)이 3이라 메모 + 요약 2건 — 실기기 실패도 이 모양이었다.
        val eps = listOf(
            episode("e1", "자전거 비밀번호 변경", "1234로 설정했으나 4321로 변경했다", listOf("자전거", "비밀번호")),
            episode("e4", "자전거 비밀번호 질문", "비서는 134라고 답했다", listOf("자전거", "비밀번호"))
        )
        coEvery { repository.search(any(), any()) } returns AppResult.Success(listOf(manual))
        coEvery { episodes.search(any(), any()) } returns AppResult.Success(eps)

        val json = run("자전거 비밀번호")

        assertTrue(json, json.contains("- (과거 대화) 1건에는 다른 값이 적혀 있습니다: 1234, 4321"))
        assertTrue("메모 값을 적은 요약은 그대로", json.contains("비서는 134라고 답했다"))
        assertTrue("줄인 요약 본문은 싣지 않는다", !json.contains("4321로 변경했다"))
        assertTrue("칩은 남긴 요약만", json.contains("\"episodeIds\":[\"e4\"]"))
        assertTrue("섞였으니 안내는 그대로", json.contains("둘이 다르면 메모의 값을 답하고"))
    }

    @Test
    fun `동점이면 직접 저장한 메모가 자동 메모보다 앞선다`() = runBlocking {
        val auto = KnowledgeNote(id = "k-auto", content = "자전거 비밀번호: 4321", createdAt = 900, updatedAt = 900, source = KnowledgeNote.SOURCE_AUTO)
        val manual = note("k-manual", "자전거 비밀번호: 134", createdAt = 1)
        coEvery { repository.search(any(), any()) } returns AppResult.Success(listOf(auto, manual))

        val json = run("자전거 비밀번호")

        assertTrue(json, json.indexOf("134") < json.indexOf("4321"))
    }

    @Test
    fun `결과 카드 근거 — 1순위 메모 id 와 충돌 건수·다른 값이 meta 에 실린다(0·38·2)`() = runBlocking {
        val manual = note("k-manual", "자전거 비밀번호는 134")
        val eps = listOf(
            episode("e1", "자전거 비밀번호 변경", "1234로 설정했으나 4321로 변경했다", listOf("자전거", "비밀번호")),
            episode("e4", "자전거 비밀번호 질문", "비서는 134라고 답했다", listOf("자전거", "비밀번호"))
        )
        coEvery { repository.search(any(), any()) } returns AppResult.Success(listOf(manual))
        coEvery { episodes.search(any(), any()) } returns AppResult.Success(eps)

        val result = org.json.JSONObject(run("자전거 비밀번호")).getJSONObject("meta").getJSONObject("result")

        assertEquals("MEMORY_RECALL", result.getString("kind"))
        assertEquals("k-manual", result.getString("ref"))
        val meta = org.json.JSONObject(result.getString("meta"))
        assertEquals(1, meta.getInt("conflictCount"))
        assertEquals(listOf("1234", "4321"), (0 until meta.getJSONArray("otherValues").length()).map { meta.getJSONArray("otherValues").getString(it) })
    }

    @Test
    fun `충돌한 과거 대화 id 가 meta 에 실린다 — 상황 칩이 연다(0·38·2 M6)`() = runBlocking {
        val manual = note("k-manual", "자전거 비밀번호는 134")
        val eps = listOf(
            episode("e1", "자전거 비밀번호 변경", "1234로 설정했으나 4321로 변경했다", listOf("자전거", "비밀번호")),
            episode("e4", "자전거 비밀번호 질문", "비서는 134라고 답했다", listOf("자전거", "비밀번호"))
        )
        coEvery { repository.search(any(), any()) } returns AppResult.Success(listOf(manual))
        coEvery { episodes.search(any(), any()) } returns AppResult.Success(eps)

        val result = org.json.JSONObject(run("자전거 비밀번호")).getJSONObject("meta").getJSONObject("result")
        val ids = org.json.JSONObject(result.getString("meta")).getJSONArray("conflictEpisodeIds")
        assertEquals(listOf("e1"), (0 until ids.length()).map { ids.getString(it) })
    }

    @Test
    fun `지난 대화만 근거면 화면 전용 meta 에 그 대화 id 가 실리고 모델 입력에는 없다(0·38·6 M3)`() = runBlocking {
        coEvery { repository.search(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { episodes.search(any(), any()) } returns AppResult.Success(listOf(episode("e9", "자전거 비밀번호 변경", "4321로 바꿨다", listOf("자전거", "비밀번호"))))

        val json = org.json.JSONObject(run("자전거 비밀번호"))
        val result = json.getJSONObject("meta").getJSONObject("result")

        assertEquals("", result.getString("ref"))
        assertEquals("e9", org.json.JSONObject(result.getString("meta")).getString("episodeId"))
        json.remove("meta") // BaseAgent.stripMeta 와 같다 — 모델이 보는 부분
        assertTrue(!json.toString().contains("episodeId"))
    }
}
