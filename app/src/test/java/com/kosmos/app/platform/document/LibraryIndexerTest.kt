package com.kosmos.app.platform.document

import android.net.Uri
import com.kosmos.app.domain.document.DocumentError
import com.kosmos.app.domain.document.DocumentResult
import com.kosmos.app.domain.document.FlowBlock
import com.kosmos.app.domain.document.FlowDocument
import com.kosmos.app.domain.document.Span
import com.kosmos.app.domain.library.LibraryDocument
import com.kosmos.app.domain.library.LibraryFailure
import com.kosmos.app.domain.library.LibraryLimits
import com.kosmos.app.domain.library.LibraryStatus
import com.kosmos.app.testing.FakeLibraryRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [LibraryIndexerTest]
 * 보관함 색인 실행(0.41.0 M3) — 형식별로 전부 읽어 조각으로 넣는가, 글자 없음·상한·열기 실패를 사유 코드로 남기는가, 멈춘 색인을 실패로 돌리는가.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryIndexerTest {

    private val repo = FakeLibraryRepository()

    private fun flowDoc(vararg paragraphs: String) = OpenedDocument.Flow(
        "규정.hwpx",
        FlowDocument(paragraphs.map { FlowBlock.Paragraph(listOf(Span(it))) })
    )

    private fun pdf(vararg pages: String?) = OpenedDocument.Pdf("교재.pdf", object : PdfPages {
        override val pageCount = pages.size
        override fun aspectRatio(index: Int) = 1.4f
        override suspend fun render(index: Int, widthPx: Int) = null
        override suspend fun text(index: Int) = pages[index]
        override fun close() = Unit
    })

    private fun indexer(result: DocumentResult<OpenedDocument>) =
        LibraryIndexer(object : DocumentOpener {
            override suspend fun open(uri: Uri, mimeTypeHint: String?) = result
        }, repo, LibraryEmbedder(repo) { null })

    private fun addAndWait(indexer: LibraryIndexer): LibraryDocument = runBlocking {
        withTimeout(5_000) { indexer.add(Uri.parse("content://doc/1"), "규정", "text/plain", now = 1).join() }
        repo.documents.value.single()
    }

    @Test
    fun `읽기 모드 문서를 조각으로 넣고 준비 상태로 바꾼다`() {
        val doc = addAndWait(indexer(DocumentResult.Ok(flowDoc("제1조(목적) 이 규정은 출장 여비를 정한다.", "제2조(일비) 일비는 2만원으로 한다."))))

        assertEquals(LibraryStatus.READY, doc.status)
        assertEquals(2, doc.chunkCount)
        assertEquals(listOf("1", "2"), repo.chunks.getValue(doc.id).map { it.article })
        assertEquals("규정 > 제1조(목적)", repo.chunks.getValue(doc.id).first().sectionPath)
    }

    @Test
    fun `PDF 는 쪽 번호를 든 조각이 되고 글자를 하나도 못 뽑으면 글자 없음으로 실패한다`() {
        val ok = addAndWait(indexer(DocumentResult.Ok(pdf("1장 서론", null, "3쪽 본문"))))
        assertEquals(listOf(1, 3), repo.chunks.getValue(ok.id).map { it.page })

        repo.documents.value = emptyList()
        val scanned = addAndWait(indexer(DocumentResult.Ok(pdf(null, ""))))
        assertEquals(LibraryStatus.FAILED, scanned.status)
        assertEquals(LibraryFailure.NO_TEXT.name, scanned.error)
    }

    @Test
    fun `열기 실패는 뷰어 오류 분류를 붙여 남기고 상한을 넘으면 너무 큼으로 실패한다`() {
        val failed = addAndWait(indexer(DocumentResult.Fail(DocumentError.ENCRYPTED)))
        assertEquals("${LibraryFailure.OPEN_FAILED.name}:${DocumentError.ENCRYPTED.name}", failed.error)

        repo.documents.value = emptyList()
        val huge = "가".repeat(LibraryLimits.MAX_DOCUMENT_CHARS / 2)
        val tooLarge = addAndWait(indexer(DocumentResult.Ok(flowDoc(huge, huge, "끝"))))
        assertEquals(LibraryFailure.TOO_LARGE.name, tooLarge.error)
    }

    @Test
    fun `앞 실행에서 색인 중에 멈춘 문서는 시작할 때 중단으로 실패 처리한다`() = runBlocking {
        repo.upsert(LibraryDocument("d1", "content://d1", "규정", null, 1, LibraryStatus.INDEXING))
        indexer(DocumentResult.Fail(DocumentError.UNREADABLE)).start()

        withTimeout(5_000) { while (repo.documents.value.single().status == LibraryStatus.INDEXING) kotlinx.coroutines.delay(20) }
        assertEquals(LibraryFailure.INTERRUPTED.name, repo.documents.value.single().error)
        assertTrue(repo.chunks.isEmpty())
    }
}
