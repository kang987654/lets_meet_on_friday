package com.kosmos.app.assistant.tool

import com.kosmos.app.assistant.agent.KosmosAgent
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.context.PromptAssembler
import com.kosmos.app.assistant.context.libraryNamesForPrompt
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.document.FlowBlock
import com.kosmos.app.domain.document.Span
import com.kosmos.app.domain.library.LibraryChunk
import com.kosmos.app.domain.library.LibraryDocument
import com.kosmos.app.domain.library.LibraryHit
import com.kosmos.app.domain.library.LibraryStatus
import com.kosmos.app.domain.library.TextEmbedder
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.ProfileRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.MessageResult
import com.kosmos.app.domain.tool.ToolNames
import com.kosmos.app.feature.document.ViewerTarget
import com.kosmos.app.runtime.gemma.GemmaTokenizer
import com.kosmos.app.runtime.gemma.KosmosToolDeclarations
import com.kosmos.app.testing.FakeLibraryRepository
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
 * [SearchDocumentsTest]
 * 문서 검색(0.41.2 M4) — 토글이 켜지고 보관함에 문서가 있을 때만 선언·트리거·예약이 붙고, 실행기는 exp78 형식 묶음과 출처를 돌려준다.
 * Robolectric — 실행기가 org.json 을 쓴다(다른 실행기 테스트와 같다).
 */
@RunWith(RobolectricTestRunner::class)
class SearchDocumentsTest {

    private fun hit(doc: String, index: Int, section: String, text: String, page: Int? = null) =
        LibraryHit("id-$doc", doc, LibraryChunk(index, "$doc > $section", null, page, text))

    private fun executor(repo: FakeLibraryRepository, embedder: TextEmbedder? = null) =
        SearchDocumentsToolExecutor(repo, { embedder }, GemmaTokenizer())

    @Test
    fun `검색어로 찾은 조각을 문서와 위치를 붙여 돌려주고 출처를 meta 에 싣는다`() = runBlocking {
        val repo = FakeLibraryRepository().apply {
            searchResults["절도"] = listOf(
                hit("형법", 7, "제2편 각칙 > 제329조(절도)", "타인의 재물을 절취한 자는 6년 이하의 징역"),
                hit("형법", 8, "제2편 각칙 > 제330조(야간주거침입절도)", "야간에 사람의 주거에 침입하여")
            )
        }

        val json = JSONObject(executor(repo).execute(ToolArguments.of(mapOf("query" to "절도")), "s1"))

        assertEquals("success", json.getString("status"))
        val data = json.getString("data")
        assertTrue(data, data.startsWith("[등록 문서에서 찾은 내용]\n1) 형법 > 제329조(절도)\n타인의 재물을"))
        assertTrue(data.contains("2) 형법 > 제330조(야간주거침입절도)"))
        val result = json.getJSONObject("meta").getJSONObject("result")
        assertEquals(MessageResult.Kind.DOC_SOURCE.name, result.getString("kind"))
        assertEquals("id-형법", result.getString("ref"))
        val sources = JSONObject(result.getString("meta")).getJSONArray(SearchDocumentsToolExecutor.META_SOURCES)
        assertEquals(2, sources.length())
        assertEquals("제329조(절도)", sources.getJSONObject(0).getString(SearchDocumentsToolExecutor.META_SECTION))
    }

    @Test
    fun `조각 본문은 정해진 글자까지만 보낸다`() = runBlocking {
        val repo = FakeLibraryRepository().apply { searchResults["긴"] = listOf(hit("교재", 0, "1장", "가".repeat(1_000))) }

        val data = JSONObject(executor(repo).execute(ToolArguments.of(mapOf("query" to "긴")), "s1")).getString("data")

        assertEquals(Constants.LIBRARY_RESULT_CHARS, data.substringAfter("1) 교재 > 1장\n").length)
    }

    @Test
    fun `못 찾으면 출처 없이 못 찾았다고 돌려준다`() = runBlocking {
        val json = JSONObject(executor(FakeLibraryRepository()).execute(ToolArguments.of(mapOf("query" to "없음")), "s1"))

        assertEquals("success", json.getString("status"))
        assertEquals("등록 문서에서 관련 내용을 찾지 못했어요.", json.getString("data"))
        assertFalse(json.has("meta"))
    }

    @Test
    fun `검색어가 여럿이면 번갈아 합치고 같은 조각은 한 번만`() = runBlocking {
        val a = hit("민법", 1, "제750조(불법행위의 내용)", "고의 또는 과실로")
        val b = hit("민법", 2, "제751조(재산 이외의 손해의 배상)", "신체, 자유")
        val c = hit("상법", 3, "제24조(명의대여자의 책임)", "타인에게 자기의 성명")
        val repo = FakeLibraryRepository().apply {
            searchResults["손해배상"] = listOf(a, b)
            searchResults["불법행위"] = listOf(a, c)
        }

        val data = JSONObject(
            executor(repo).execute(ToolArguments.of(mapOf("queries" to listOf("손해배상", "불법행위"))), "s1")
        ).getString("data")

        assertTrue(data, data.contains("1) 민법 > 제750조") && data.contains("2) 민법 > 제751조") && data.contains("3) 상법 > 제24조"))
    }

    @Test
    fun `모델이 있으면 임베더를 넘기고 없으면 바이그램만`() = runBlocking {
        val embedder = object : TextEmbedder {
            override val modelId = "m"
            override suspend fun embedQuery(query: String) = floatArrayOf(1f)
            override suspend fun embedDocuments(inputs: List<String>) = inputs.map { floatArrayOf(1f) }
        }
        val repo = FakeLibraryRepository()

        executor(repo, embedder).execute(ToolArguments.of(mapOf("query" to "q")), "s1")
        executor(repo, null).execute(ToolArguments.of(mapOf("query" to "q")), "s1")

        assertEquals(listOf<TextEmbedder?>(embedder, null), repo.searched.map { it.second })
    }

    // ── 에이전트·프롬프트 ──

    private fun context(docSearch: Boolean) = ContextBuilder.Context(
        recentConversations = emptyList(), sessionId = "s1", responseStyle = "DEFAULT", docSearchEnabled = docSearch
    )

    private fun agent() = KosmosAgent(
        modelRunner = mockk(relaxed = true),
        toolRegistry = mockk(relaxed = true),
        auditTrailService = mockk(relaxed = true),
        conversationRepository = mockk(relaxed = true),
        approvalCoordinator = mockk(relaxed = true),
        promptAssembler = PromptAssembler()
    )

    @Test
    fun `문서 검색이 켜졌을 때만 툴과 트리거 줄이 붙는다`() {
        val off = agent().availableTools(context(false))
        val on = agent().availableTools(context(true))
        assertFalse(ToolNames.SEARCH_DOCUMENTS in off)
        assertEquals(off + ToolNames.SEARCH_DOCUMENTS, on)

        val assembler = PromptAssembler()
        val offPrompt = assembler.assembleWithTools(context(false), "안녕", off, "assistant")
        val onPrompt = assembler.assembleWithTools(context(true), "안녕", on, "assistant")
        assertFalse(offPrompt.systemInstruction.contains("search_documents"))
        assertTrue(onPrompt.systemInstruction.contains("call `search_documents` first"))
        assertTrue(KosmosToolDeclarations.providersFor(on).size == on.size)
        assertEquals(ToolNames.SEARCH_DOCUMENTS, KosmosToolDeclarations.canonicalName("search_documents"))
    }

    @Test
    fun `켜짐 트리거에 되묻기 금지와 등록 문서 이름이 붙는다`() {
        val on = agent().availableTools(context(true))
        val prompt = PromptAssembler().assembleWithTools(context(true).copy(libraryNames = listOf("민법", "형법")), "안녕", on, "assistant")

        assertTrue(prompt.systemInstruction.contains("Never ask which document"))
        assertTrue(prompt.systemInstruction.contains("Registered documents: 민법, 형법."))
    }

    @Test
    fun `문서 이름은 색인이 끝난 것만 등록 순으로 확장자 없이 상한까지`() {
        fun doc(id: String, name: String, at: Long, status: LibraryStatus = LibraryStatus.READY) =
            LibraryDocument(id, "content://$id", name, null, at, status)
        val docs = listOf(doc("b", "형법.pdf", 2), doc("a", "민법.docx", 1), doc("c", "상법.hwpx", 3, LibraryStatus.FAILED))
        assertEquals(listOf("민법", "형법"), libraryNamesForPrompt(docs))

        val many = (1..40).map { doc("d$it", "문서이름$it.pdf", it.toLong()) }
        val names = libraryNamesForPrompt(many)
        assertEquals("…", names.last())
        assertTrue(names.dropLast(1).joinToString(", ").length <= Constants.LIBRARY_NAMES_MAX_CHARS)

        val long = listOf(doc("x", "가".repeat(300) + ".pdf", 1))
        assertEquals(Constants.LIBRARY_NAMES_MAX_CHARS + 1, libraryNamesForPrompt(long).single().length)
    }

    // ── 컨텍스트: 토글 + 보관함 ──

    private fun builder(toggle: Boolean, docs: List<LibraryDocument>, history: List<ChatMessage> = emptyList()): ContextBuilder {
        val conversations: ConversationRepository = mockk {
            coEvery { getRecentBySession(any(), any()) } returns AppResult.Success(history)
        }
        val settings: SettingsDataStore = mockk(relaxed = true) {
            every { responseStyleFlow } returns flowOf("DEFAULT")
            every { maxTokensFlow } returns flowOf(Constants.MIN_CONTEXT_TOKENS)
            every { webSearchEnabledFlow } returns flowOf(false)
            every { docSearchEnabledFlow } returns flowOf(toggle)
        }
        val profile: ProfileRepository = mockk { coEvery { getEntries() } returns AppResult.Success(emptyList()) }
        return ContextBuilder(conversations, GemmaTokenizer(), settings, profile, FakeLibraryRepository(docs))
    }

    private val ready = LibraryDocument("d1", "content://1", "민법", null, 1, LibraryStatus.READY, chunkCount = 3)

    @Test
    fun `토글이 켜져도 쓸 문서가 없으면 끈 것과 같다`() = runBlocking {
        val failed = ready.copy(status = LibraryStatus.FAILED)
        assertTrue((builder(true, listOf(ready)).build("s1") as AppResult.Success).data.docSearchEnabled)
        assertFalse((builder(true, listOf(failed)).build("s1") as AppResult.Success).data.docSearchEnabled)
        assertFalse((builder(true, emptyList()).build("s1") as AppResult.Success).data.docSearchEnabled)
        assertFalse((builder(false, listOf(ready)).build("s1") as AppResult.Success).data.docSearchEnabled)
    }

    @Test
    fun `켜진 턴만 선언 몫을 더 예약해 대화 기록이 그만큼 줄어든다`() = runBlocking {
        val history = (1..40).map {
            ChatMessage(
                id = "m$it", sessionId = "s1", role = if (it % 2 == 0) ChatMessage.Role.ASSISTANT else ChatMessage.Role.USER,
                content = "대화 기록 문장 $it 입니다", inputType = InputType.TEXT, createdAt = it.toLong()
            )
        }
        val off = (builder(false, listOf(ready), history).build("s1") as AppResult.Success).data.recentConversations
        val on = (builder(true, listOf(ready), history).build("s1") as AppResult.Success).data.recentConversations

        assertTrue("꺼짐 ${off.size} · 켜짐 ${on.size}", on.size < off.size)
        assertEquals("남는 쪽은 최신 기록", off.last(), on.last())
    }

    // ── 뷰어 위치 ──

    private fun para(text: String) = FlowBlock.Paragraph(listOf(Span(text)))

    @Test
    fun `출처 머리말로 흐름 문서의 블록을 찾는다`() {
        val blocks = listOf(para("제1편 총칙"), para("제161조(공휴일 등과 기간의 만료점) 기간의 말일이"), para("제162조(채권, 재산권의 소멸시효) ①채권은 10년간"))

        assertEquals(2, ViewerTarget.blockIndexOf(blocks, "제162조(채권, 재산권의 소멸시효)"))
        assertEquals("여러 조각 표시는 떼고 찾는다", 2, ViewerTarget.blockIndexOf(blocks, "제162조(채권, 재산권의 소멸시효) (2/3)"))
        assertEquals("제목이 달라도 조 번호로", 1, ViewerTarget.blockIndexOf(blocks, "제161조(기간의 만료점)"))
        assertNull(ViewerTarget.blockIndexOf(blocks, "제999조(없음)"))
        assertNull(ViewerTarget.blockIndexOf(blocks, null))
    }
}
