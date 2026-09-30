package com.kosmos.app.integration

import com.kosmos.app.assistant.tool.SearchMemoryToolExecutor
import com.kosmos.app.assistant.tool.ToolArguments
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.TextEmbedder
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.usecase.SaveKnowledgeUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * [MemoryPipelineIntegrationTest]
 * "기억해줘" 로 저장한 내용이 나중에 `SearchMemory` 툴로 다시 나오는지 검증합니다.
 *
 * [WHY] 예전에는 저장 → **매 턴 자동 RAG 주입**(`ContextBuilder` 가 벡터 검색 결과를 SYSTEM
 * 메시지로 삽입)을 검증했다. 그 경로는 없앴다 — 임베더가 영어 전용이라 한국어 벡터 검색이
 * 무작위였다(ADR-013). 이제 회상은 모델이 부르는 툴이므로 그 계약을 대신 고정한다.
 */
@RunWith(RobolectricTestRunner::class)
class MemoryPipelineIntegrationTest {

    private val repository: KnowledgeRepository = mockk()
    private val embedder: TextEmbedder = mockk()

    // [WHY] 에피소드 저장소·토크나이저가 executor 에 추가됐다(ADR-022 회수 통합).
    // 이 테스트의 관심사는 메모 파이프라인이므로 에피소드 쪽은 빈 결과로 고정한다.
    private fun searchMemoryExecutor(): SearchMemoryToolExecutor {
        val episodes: com.kosmos.app.domain.memory.EpisodeRepository = io.mockk.mockk {
            coEvery { search(any(), any()) } returns AppResult.Success(emptyList())
            coEvery { searchByTags(any(), any()) } returns AppResult.Success(emptyList())
            coEvery { getEpisodes(any(), any()) } returns AppResult.Success(emptyList())
        }
        val tokenizer: com.kosmos.app.domain.tool.Tokenizer = io.mockk.mockk {
            io.mockk.every { sizeInTokens(any()) } answers { firstArg<String>().length / 3 }
        }
        return SearchMemoryToolExecutor(repository, episodes, tokenizer)
    }

    private fun note(content: String, tags: List<String> = emptyList()) = KnowledgeNote(
        id = UUID.randomUUID().toString(),
        content = content,
        tags = tags,
        embedding = null,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    @Test
    fun `저장한 기억이 키워드 조회로 되돌아온다`() = runBlocking {
        val saved = slot<KnowledgeNote>()
        coEvery { embedder.embed(any()) } returns AppResult.Success(floatArrayOf(0.1f, 0.2f))
        coEvery { repository.save(capture(saved)) } returns AppResult.Success(Unit)

        val saveResult = SaveKnowledgeUseCase(repository, embedder)("나 매운 거 못 먹어", listOf("food"))
        assertTrue(saveResult is AppResult.Success)
        assertEquals("나 매운 거 못 먹어", saved.captured.content)

        coEvery { repository.search("매운", any()) } returns AppResult.Success(listOf(saved.captured))
        coEvery { repository.searchByTags(any(), any()) } returns AppResult.Success(emptyList())

        val json = searchMemoryExecutor()
            .execute(ToolArguments.of(mapOf("keyword" to "매운")), "s1")

        assertTrue("성공으로 돌아와야 한다: $json", json.contains("\"status\":\"success\""))
        assertTrue("저장한 내용이 담겨야 한다: $json", json.contains("나 매운 거 못 먹어"))
    }

    @Test
    fun `임베딩이 실패해도 저장은 성공한다`() = runBlocking {
        // [WHY] 예전에는 임베딩 실패가 저장 자체를 실패시켰다. 지금 임베딩은 어떤 조회
        // 경로에서도 읽히지 않으므로, 그 이유로 사용자의 "기억해줘"를 버리면 안 된다.
        coEvery { embedder.embed(any()) } returns AppResult.Failure(AppError.SearchError("모델 없음"))
        val saved = slot<KnowledgeNote>()
        coEvery { repository.save(capture(saved)) } returns AppResult.Success(Unit)

        val result = SaveKnowledgeUseCase(repository, embedder)("자전거 비밀번호는 1234", emptyList())

        assertTrue("저장은 성공해야 한다", result is AppResult.Success)
        assertEquals(null, saved.captured.embedding)
    }

    // --- SearchMemory 툴 계약 ---

    @Test
    fun `태그로도 찾는다`() = runBlocking {
        // [WHY] 모델은 의미로 키워드를 뽑으므로 본문과 글자가 어긋나는 경우가 실측됐다
        // ("좋아하는 것" ↔ "커피보다 녹차를 더 좋아함"). 저장 시 붙인 태그가 두 번째 통로다.
        val tagged = note("커피보다 녹차를 더 좋아함", listOf("선호도"))
        coEvery { repository.search(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { repository.searchByTags(listOf("선호도"), any()) } returns AppResult.Success(listOf(tagged))

        val json = searchMemoryExecutor()
            .execute(ToolArguments.of(mapOf("keyword" to "선호도")), "s1")

        assertTrue("태그 경로로 찾아야 한다: $json", json.contains("커피보다 녹차를 더 좋아함"))
    }

    @Test
    fun `저장된 기억이 아예 없으면 없다고만 답하게 한다`() = runBlocking {
        coEvery { repository.search(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { repository.searchByTags(any(), any()) } returns AppResult.Success(emptyList())
        coEvery { repository.searchRecent(any()) } returns AppResult.Success(emptyList())

        val json = searchMemoryExecutor()
            .execute(ToolArguments.of(mapOf("keyword" to "여권번호")), "s1")

        assertTrue(json.contains("저장된 기억이 하나도 없습니다"))
        assertTrue(json.contains("추측하지 마세요"))
    }

    @Test
    fun `조회 실패는 오류로 되돌린다`() = runBlocking {
        coEvery { repository.search(any(), any()) } returns AppResult.Failure(AppError.SearchError("db"))
        coEvery { repository.searchByTags(any(), any()) } returns AppResult.Success(emptyList())

        val json = searchMemoryExecutor()
            .execute(ToolArguments.of(mapOf("keyword" to "자전거")), "s1")

        assertTrue(json.contains("\"status\":\"error\""))
    }

    @Test
    fun `키워드가 비면 인자 오류로 걸러진다`() {
        // [WHY] 빈 검색어는 LIKE '%%' 가 되어 테이블 전체를 긁는다. 래퍼가 먼저 막는지 고정한다.
        val executor = searchMemoryExecutor()
        val error = runCatching {
            runBlocking { executor.execute(ToolArguments.of(mapOf("keyword" to "  ")), "s1") }
        }.exceptionOrNull()

        assertNotNull("ToolArgumentException 이어야 한다", error)
        assertTrue(error is com.kosmos.app.assistant.tool.ToolArgumentException)
    }
}
