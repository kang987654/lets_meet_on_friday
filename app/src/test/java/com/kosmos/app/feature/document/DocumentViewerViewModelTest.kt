package com.kosmos.app.feature.document

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.document.DocumentError
import com.kosmos.app.domain.document.DocumentResult
import com.kosmos.app.domain.document.Sheet
import com.kosmos.app.platform.document.DocumentOpener
import com.kosmos.app.platform.document.OpenedDocument
import com.kosmos.app.testing.InMemoryPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [DocumentViewerViewModelTest]
 * 문서 열기 상태 전이 — 첫 시트만 먼저 읽고, 탭을 누를 때 다음 시트를 읽으며, 같은 URI 는 다시 열지 않는다.
 * 화면이 사라지면 문서를 닫는다(캐시 복사본 삭제).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentViewerViewModelTest {

    private val uri: Uri = mockk()
    private val sheetReads = mutableListOf<Int>()
    private var closed = 0
    private var opens = 0
    private var result: DocumentResult<OpenedDocument> = DocumentResult.Ok(spreadsheet())

    private fun spreadsheet(failSheet: Int? = null) = OpenedDocument.Spreadsheet(
        fileName = "가계부.xlsx",
        sheetNames = listOf("1월", "2월"),
        loader = { index, _ ->
            sheetReads += index
            if (index == failSheet) DocumentResult.Fail(DocumentError.TOO_LARGE)
            else DocumentResult.Ok(Sheet(name = "${index + 1}월", rows = emptyList(), columnCount = 0))
        },
        onClose = { closed++ }
    )

    private val opener = object : DocumentOpener {
        override suspend fun open(uri: Uri, mimeTypeHint: String?): DocumentResult<OpenedDocument> {
            opens++
            return result
        }
    }

    private val offered = mutableListOf<com.kosmos.app.platform.share.SharedInput>()
    private val shareHandler: com.kosmos.app.platform.share.ShareIntentHandler = mockk {
        every { offer(any()) } answers { offered += firstArg<com.kosmos.app.platform.share.SharedInput>() }
    }

    private val store = ViewModelStore()
    private lateinit var viewModel: DocumentViewerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = DocumentViewerViewModel(opener, SettingsDataStore(InMemoryPreferences()), shareHandler) as T
        }
        viewModel = ViewModelProvider.create(store, factory)[DocumentViewerViewModel::class]
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `첫 시트만 먼저 읽고 탭을 누르면 그 시트를 읽는다`() {
        viewModel.load(uri)

        val first = viewModel.state.value as DocumentViewerState.Spreadsheet
        assertEquals(listOf("1월", "2월"), first.sheetNames)
        assertEquals("1월", first.sheet?.name)
        assertEquals(listOf(0), sheetReads)

        viewModel.selectSheet(1)
        val second = viewModel.state.value as DocumentViewerState.Spreadsheet
        assertEquals(1, second.selected)
        assertEquals("2월", second.sheet?.name)
        assertEquals(listOf(0, 1), sheetReads)

        viewModel.selectSheet(1)
        viewModel.selectSheet(5)
        assertEquals("같은 탭·없는 탭은 읽지 않는다", listOf(0, 1), sheetReads)
    }

    @Test
    fun `읽기 모드 문서와 글자 크기 단계`() {
        val doc = com.kosmos.app.domain.document.FlowDocument(emptyList())
        result = DocumentResult.Ok(OpenedDocument.Flow("보고서.hwpx", doc))
        viewModel.load(uri)
        val state = viewModel.state.value as DocumentViewerState.Flow
        assertEquals("보고서.hwpx", state.fileName)

        assertEquals(1, viewModel.textScaleStep.value)
        viewModel.changeTextScale(1)
        viewModel.changeTextScale(1)
        assertEquals("크게(2)에서 멈춘다", 2, viewModel.textScaleStep.value)
    }

    @Test
    fun `행 번호 두 번으로 범위를 고르고 채팅으로 넘긴다`() {
        val sheet = com.kosmos.app.domain.document.Sheet(
            name = "1월",
            rows = (0..5).map { r -> com.kosmos.app.domain.document.SheetRow(r, listOf(com.kosmos.app.domain.document.SheetCell(0, if (r == 0) "항목" else "값$r"))) },
            columnCount = 1
        )
        result = DocumentResult.Ok(OpenedDocument.Spreadsheet("가계부.xlsx", listOf("1월"), { _, _ -> DocumentResult.Ok(sheet) }))
        viewModel.load(uri)
        assertEquals("고르기 전에는 보낼 것이 없다", false, viewModel.sendToChat())

        viewModel.startSelection()
        viewModel.tapRow(2)
        viewModel.tapRow(4)
        val rows = viewModel.selection.value as ChatSelection.Rows
        assertEquals(2..4, rows.range)
        assertEquals("머리 행이 열 이름으로 붙는다(exp46c 형식)", "항목: 값2\n항목: 값3\n항목: 값4", rows.preview!!.text)

        viewModel.tapRow(1)
        assertEquals("범위가 정해진 뒤의 탭은 새로 시작", 1..1, (viewModel.selection.value as ChatSelection.Rows).range)
        viewModel.tapRow(3)

        assertTrue(viewModel.sendToChat())
        val doc = offered.single() as com.kosmos.app.platform.share.SharedInput.Document
        assertEquals("가계부.xlsx", doc.fileName)
        assertTrue(doc.textContent.contains("값1") && doc.textContent.contains("값3") && !doc.textContent.contains("값4"))
        assertEquals("보낸 뒤에는 고르기를 끝낸다", null, viewModel.selection.value)
    }

    @Test
    fun `읽기 모드 블록은 눌러서 넣고 빼고, PDF 글자를 못 꺼내는 기기는 알린다`() {
        val doc = com.kosmos.app.domain.document.FlowDocument(
            listOf("첫 문단", "둘째 문단", "셋째 문단").map { com.kosmos.app.domain.document.FlowBlock.Paragraph(listOf(com.kosmos.app.domain.document.Span(it))) }
        )
        result = DocumentResult.Ok(OpenedDocument.Flow("회의록.docx", doc))
        viewModel.load(uri)
        viewModel.startSelection()
        viewModel.tapBlock(2)
        viewModel.tapBlock(0)
        viewModel.tapBlock(2)
        val blocks = viewModel.selection.value as ChatSelection.Blocks
        assertEquals(setOf(0), blocks.indices)
        assertEquals("첫 문단", blocks.preview?.text)

        val pages = object : com.kosmos.app.platform.document.PdfPages {
            override val pageCount = 3
            override fun aspectRatio(index: Int) = 1.4f
            override suspend fun render(index: Int, widthPx: Int) = null
            override fun close() = Unit
        }
        result = DocumentResult.Ok(OpenedDocument.Pdf("보고서.pdf", pages))
        viewModel.load(mockk())
        viewModel.startSelection()
        assertTrue((viewModel.selection.value as ChatSelection.PdfPage).unsupported)
        assertEquals(false, viewModel.sendToChat())
    }

    @Test
    fun `같은 URI 는 다시 열지 않는다`() {
        viewModel.load(uri)
        viewModel.load(uri)
        assertEquals(1, opens)
    }

    @Test
    fun `열기 실패와 시트 하나의 실패`() {
        result = DocumentResult.Fail(DocumentError.ENCRYPTED)
        viewModel.load(uri)
        assertEquals(DocumentViewerState.Failed(DocumentError.ENCRYPTED), viewModel.state.value)

        result = DocumentResult.Ok(spreadsheet(failSheet = 1))
        viewModel.load(mockk())
        viewModel.selectSheet(1)
        val state = viewModel.state.value as DocumentViewerState.Spreadsheet
        assertEquals("다른 시트는 여전히 고를 수 있다", DocumentError.TOO_LARGE, state.sheetError)
        viewModel.selectSheet(0)
        assertEquals("1월", (viewModel.state.value as DocumentViewerState.Spreadsheet).sheet?.name)
    }

    @Test
    fun `화면이 사라지면 문서를 닫는다`() {
        viewModel.load(uri)
        store.clear()
        assertEquals(1, closed)
    }

    @Test
    fun `다른 문서를 열면 앞 문서를 닫는다`() {
        viewModel.load(uri)
        viewModel.load(mockk())
        assertEquals(1, closed)
        assertTrue(viewModel.state.value is DocumentViewerState.Spreadsheet)
    }
}
