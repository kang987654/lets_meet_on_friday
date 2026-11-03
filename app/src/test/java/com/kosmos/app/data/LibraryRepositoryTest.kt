package com.kosmos.app.data

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.repository.DataResetRepositoryImpl
import com.kosmos.app.data.local.repository.LibraryRepositoryImpl
import com.kosmos.app.domain.library.LibraryChunker
import com.kosmos.app.domain.library.LibraryDocument
import com.kosmos.app.domain.library.LibraryHit
import com.kosmos.app.domain.library.LibraryStatus
import com.kosmos.app.domain.memory.ResetTarget
import com.kosmos.app.testing.InMemoryKosmosDbRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [LibraryRepositoryTest]
 * 문서 보관함 저장소(0.41.0 M2) — 실제 Room 스키마(FTS4 포함) 위에서 조각 적재·검색·삭제·상태.
 *
 * [WHY] 검색은 FTS4 질의 → matchinfo → 재순위의 세 단계라, 순수 함수 테스트(LibrarySearchTest)만으로는 SQL 과 이어지는 곳을 못 본다.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {

    @get:Rule
    val dbRule = InMemoryKosmosDbRule()

    private lateinit var repo: LibraryRepositoryImpl

    private val civil = listOf(
        "제1편 총칙", "제2장 인", "제4조(성년) 사람은 19세로 성년에 이르게 된다.",
        "제7장 소멸시효", "제162조(채권, 재산권의 소멸시효) ①채권은 10년간 행사하지 아니하면 소멸시효가 완성한다.",
        "제3편 채권", "제750조(불법행위의 내용) 고의 또는 과실로 인한 위법행위로 타인에게 손해를 가한 자는 그 손해를 배상할 책임이 있다."
    )
    private val criminal = listOf(
        "제2편 각칙", "제38장 절도와 강도의 죄", "제329조(절도) 타인의 재물을 절취한 자는 6년 이하의 징역 또는 1천만원 이하의 벌금에 처한다.",
        "제24장 살인의 죄", "제250조(살인, 존속살해) ①사람을 살해한 자는 사형, 무기 또는 5년 이상의 징역에 처한다."
    )

    private fun <T> AppResult<T>.ok(): T {
        assertTrue(toString(), this is AppResult.Success)
        return (this as AppResult.Success).data
    }

    private fun doc(id: String, name: String) = LibraryDocument(id, "content://doc/$id", name, "text/plain", addedAt = 1, status = LibraryStatus.INDEXING)

    private fun add(id: String, name: String, lines: List<String>) = runBlocking {
        repo.upsert(doc(id, name)).ok()
        val chunks = LibraryChunker.chunk(name, LibraryChunker.fromPlain(lines.joinToString("\n")))
        repo.replaceChunks(id, chunks, lines.sumOf { it.length }).ok()
    }

    // [WHY] Flow 관찰(observeDocuments) 대신 일반 조회 — 테스트가 DB 를 닫은 뒤 Room 무효화 갱신이 백그라운드에서 터져 다음 테스트로 넘어갔다.
    private suspend fun documents() = dbRule.db.libraryDao().documents().mapNotNull { repo.document(it.id).ok() }

    private fun search(q: String): List<LibraryHit> = runBlocking { repo.search(q, 3).ok() }

    @Before
    fun setUp() {
        repo = LibraryRepositoryImpl(dbRule.db)
        add("d1", "민법", civil)
        add("d2", "형법", criminal)
    }

    @Test
    fun `조각을 넣으면 문서가 준비 상태가 되고 조각 수와 글자 수를 든다`() = runBlocking {
        val docs = documents().associateBy { it.id }
        assertEquals(LibraryStatus.READY, docs.getValue("d1").status)
        assertEquals(3, docs.getValue("d1").chunkCount)
        assertEquals(civil.sumOf { it.length } + criminal.sumOf { it.length }, repo.totalChars().ok())
        assertEquals(criminal.sumOf { it.length }, repo.totalChars(excludeId = "d1").ok())
    }

    @Test
    fun `같은 용어 질의는 색인 후보에서 맞는 조를 1위로 찾고 문서 이름을 붙인다`() {
        val hit = search("소멸시효 기간").first()
        assertEquals("민법", hit.documentName)
        assertEquals("162", hit.chunk.article)
        assertEquals("329", search("절도 처벌").first().chunk.article)
    }

    @Test
    fun `조문 번호 질의는 법령 이름과 함께 구조 열로 찾는다`() {
        val hit = search("형법 제250조").first()
        assertEquals("d2", hit.documentId)
        assertEquals("250", hit.chunk.article)
        assertEquals("d1", search("민법 750조").first().documentId)
    }

    @Test
    fun `다시 넣으면 앞 조각과 색인을 바꾸고 지우면 문서와 조각과 색인이 모두 사라진다`() = runBlocking {
        add("d2", "형법", listOf("제1조(범죄의 성립과 처벌) 범죄의 성립과 처벌은 행위 시의 법률에 따른다."))
        assertEquals(1, documents().single { it.id == "d2" }.chunkCount)
        assertTrue("앞 조각의 색인이 남지 않는다", search("절도").none { it.documentId == "d2" })

        repo.remove("d1").ok()
        assertTrue(documents().none { it.id == "d1" })
        assertTrue(search("소멸시효").none { it.documentId == "d1" })
        dbRule.db.query("SELECT COUNT(*) FROM library_fts", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }

    @Test
    fun `색인 중에 멈춘 문서는 실패로 바뀌고 빈 보관함과 빈 질의는 빈 결과다`() = runBlocking {
        repo.upsert(doc("d3", "규정")).ok()
        assertEquals(1, repo.markInterrupted("중단됨").ok())
        val d3 = repo.document("d3").ok()
        assertEquals(LibraryStatus.FAILED, d3?.status)
        assertEquals("중단됨", d3?.error)

        assertEquals(emptyList<LibraryHit>(), search("   "))
        DataResetRepositoryImpl(dbRule.db).reset(setOf(ResetTarget.LIBRARY)).ok()
        assertEquals(emptyList<LibraryHit>(), search("소멸시효"))
        assertTrue(documents().isEmpty())
    }
}
