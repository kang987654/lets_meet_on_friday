package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.library.Int8Vector
import com.kosmos.app.domain.library.TextEmbedder
import com.kosmos.app.domain.library.normalized
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.MemorySemanticStatus
import com.kosmos.app.domain.memory.MemoryVectorInput
import com.kosmos.app.domain.memory.MemoryVectorKey
import com.kosmos.app.domain.memory.MemoryVectorRepository
import com.kosmos.app.domain.memory.ProfileSuggestionRepository
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.feature.drawer.DrawerViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [SemanticMemorySearchTest]
 * 기억 뜻 검색 연결(0.41.3 M3) — 준비됐으면 `search_memory`·서랍이 결합 순위로 교차 언어·바꿔 말함을 찾고, 준비 전이면 지금의 어휘 경로 그대로.
 * 임베더는 낱말로 축을 고르는 가짜 — "notebook password" 처럼 글자가 안 겹치는 질문을 뜻으로만 찾게 만든다.
 */
@RunWith(RobolectricTestRunner::class)
class SemanticMemorySearchTest {

    /** 개념 축: 0 노트북 암호, 1 알레르기, 2 치과, 3 자전거. */
    private class FakeEmbedder : TextEmbedder {
        override val modelId = "m"
        private val axes = listOf(
            listOf("노트북", "notebook", "laptop", "PIN", "password"), listOf("땅콩", "알레르기", "allerg"),
            listOf("치과", "dentist", "teeth"), listOf("자전거", "bike", "bicycle")
        )

        fun vector(text: String) = FloatArray(axes.size) { i -> if (axes[i].any { text.contains(it, ignoreCase = true) }) 1f else 0.05f }.normalized()
        override suspend fun embedQuery(query: String) = vector(query)
        override suspend fun embedDocuments(inputs: List<String>) = inputs.map { vector(it) }
    }

    private val embedder = FakeEmbedder()

    private val notes = listOf(
        KnowledgeNote(id = "k1", content = "노트북 로그인 PIN 은 0912", tags = listOf("노트북"), createdAt = 1, updatedAt = 1),
        KnowledgeNote(id = "k2", content = "땅콩을 먹으면 두드러기가 남", tags = listOf("땅콩"), createdAt = 2, updatedAt = 2),
        KnowledgeNote(id = "k3", content = "자전거 자물쇠 4821", tags = listOf("자전거"), createdAt = 3, updatedAt = 3)
    )
    private val episodes = listOf(
        Episode(
            id = "e1", sessionId = "s", status = EpisodeStatus.SUMMARIZED, title = "치과 정기 검진", summary = "강남역 미소치과에서 검진을 받기로 했다",
            tags = listOf("치과"), startAt = 0, endAt = 1, messageCount = 2, retryCount = 0, createdAt = 4, updatedAt = 4
        ),
        Episode(
            id = "e2", sessionId = "s", status = EpisodeStatus.SUMMARIZED, title = "주말 캠핑 계획", summary = "토요일에 가평으로 캠핑을 가기로 했다",
            tags = listOf("캠핑"), startAt = 0, endAt = 1, messageCount = 2, retryCount = 0, createdAt = 5, updatedAt = 5
        )
    )

    private class FakeVectors(private val stored: Map<MemoryVectorKey, Int8Vector>, private val targets: Int) : MemoryVectorRepository {
        override suspend fun status(modelId: String?) = AppResult.Success(MemorySemanticStatus(modelId, targets, stored.size))
        override suspend fun pending(modelId: String, limit: Int) = AppResult.Success(emptyList<MemoryVectorInput>())
        override suspend fun save(modelId: String, vectors: List<Pair<MemoryVectorKey, Int8Vector>>) = AppResult.Success(Unit)
        override suspend fun vectors(modelId: String) = AppResult.Success(stored)
        override suspend fun clear() = AppResult.Success(Unit)
    }

    private fun allVectors(): Map<MemoryVectorKey, Int8Vector> =
        notes.associate { MemoryVectorKey.note(it.id) to Int8Vector.quantize(embedder.vector(it.content + " " + it.tags.joinToString(" "))) } +
            episodes.associate { MemoryVectorKey.episode(it.id) to Int8Vector.quantize(embedder.vector("${it.title} ${it.summary}")) }

    private fun ranker(ready: Boolean = true, installed: Boolean = true): MemorySemanticRanker {
        val stored = allVectors().let { if (ready) it else it - MemoryVectorKey.note("k3") }
        return MemorySemanticRanker(FakeVectors(stored, targets = notes.size + episodes.size)) { if (installed) embedder else null }
    }

    private val noteRepo: KnowledgeRepository = mockk {
        coEvery { search(any(), any()) } answers { AppResult.Success(notes.filter { it.content.contains(firstArg<String>(), ignoreCase = true) }) }
        coEvery { searchByTags(any(), any()) } answers { AppResult.Success(notes.filter { n -> firstArg<List<String>>().any { it in n.tags } }) }
        coEvery { searchRecent(any()) } returns AppResult.Success(notes)
        coEvery { getNotes(any(), any()) } returns AppResult.Success(notes)
    }
    private val episodeRepo: EpisodeRepository = mockk {
        coEvery { search(any(), any()) } answers { AppResult.Success(episodes.filter { "${it.title} ${it.summary}".contains(firstArg<String>()) }) }
        coEvery { searchByTags(any(), any()) } answers { AppResult.Success(episodes.filter { firstArg<String>() in it.tags }) }
        coEvery { getEpisodes(any(), any()) } returns AppResult.Success(episodes)
    }
    private val tokenizer = object : Tokenizer {
        override fun sizeInTokens(text: String) = text.length / 3
    }

    private fun executor(semantic: MemorySemanticRanker?) =
        SearchMemoryToolExecutor(noteRepo, episodeRepo, tokenizer, conversations = null, semantic = semantic)

    private fun data(json: String) = JSONObject(json).getString("data")

    @Test
    fun `준비됐으면 글자가 안 겹치는 영어 질문도 뜻으로 찾는다`() = runBlocking {
        val result = data(executor(ranker()).execute(ToolArguments.of(mapOf("keyword" to "notebook password")), "s1"))

        val firstMemo = result.lines().first { it.startsWith("- ") }
        assertTrue(result, firstMemo.contains("노트북 로그인 PIN 은 0912"))
        assertFalse("태그 목록 폴백이 아니다", result.contains("일치하는 기억이 없습니다"))
    }

    @Test
    fun `준비 전이나 모델이 없으면 지금 결과와 같다`() = runBlocking {
        val args = ToolArguments.of(mapOf("keyword" to "notebook password"))
        val lexical = executor(null).execute(args, "s1")

        assertEquals(lexical, executor(ranker(ready = false)).execute(args, "s1"))
        assertEquals(lexical, executor(ranker(installed = false)).execute(args, "s1"))
        assertTrue(data(lexical).contains("일치하는 기억이 없습니다"))
    }

    @Test
    fun `근거 카드는 1위가 메모이거나 낱말이 겹친 대화가 있을 때만`() = runBlocking {
        suspend fun card(keyword: String) = JSONObject(executor(ranker()).execute(ToolArguments.of(mapOf("keyword" to keyword)), "s1"))
            .optJSONObject("meta")?.optJSONObject("result")

        assertTrue("영어로 물어 찾은 메모는 카드가 붙는다", card("notebook password") != null)
        // 1위가 낱말이 안 겹치는 대화뿐 — 모델이 "없다"고 답할 수 있는데 "지난 대화에서 찾았어요" 카드가 붙으면 안 된다(실기기).
        assertNull(card("teeth"))
    }

    @Test
    fun `대화 요약도 같은 순위에 든다`() = runBlocking {
        val result = data(executor(ranker()).execute(ToolArguments.of(mapOf("keyword" to "dentist")), "s1"))

        assertTrue(result, result.contains("미소치과"))
    }

    @Test
    fun `순위기는 준비 전·벡터 모자람·빈 질의에 null`() = runBlocking {
        val sparse = mapOf(MemoryVectorKey.note("k1") to 0.0, MemoryVectorKey.note("k3") to 0.0)
        assertNull(ranker(ready = false).rank("bike", sparse, 3))
        assertNull(ranker(installed = false).rank("bike", sparse, 3))
        assertNull(ranker().rank(" ", sparse, 3))
        assertEquals(MemoryVectorKey.note("k3"), ranker().rank("bike", sparse, 3)?.first())
    }

    // ── 서랍 ──

    private val suggestions: ProfileSuggestionRepository = mockk { every { observePending() } returns flowOf(emptyList()) }

    @Test
    fun `서랍 검색은 낱말마다도 찾는다`() = runBlocking {
        val result = DrawerViewModel(episodeRepo, suggestions).search("정기 검진 예약")

        assertEquals(listOf("e1"), result!!.map { it.id })
    }

    @Test
    fun `서랍 검색은 준비됐으면 뜻으로 찾은 대화를 앞에 둔다`() = runBlocking {
        assertTrue("어휘로는 못 찾는다", DrawerViewModel(episodeRepo, suggestions).search("teeth checkup")!!.isEmpty())

        val result = DrawerViewModel(episodeRepo, suggestions, ranker()).search("teeth checkup")

        assertEquals("1위 근처만 — 무관한 캠핑 대화는 빈자리를 채우지 않는다", listOf("e1"), result!!.map { it.id })
    }
}
