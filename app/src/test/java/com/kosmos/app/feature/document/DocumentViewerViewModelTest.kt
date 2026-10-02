package com.kosmos.app.feature.document

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.kosmos.app.domain.document.DocumentError
import com.kosmos.app.domain.document.DocumentResult
import com.kosmos.app.domain.document.Sheet
import com.kosmos.app.platform.document.DocumentOpener
import com.kosmos.app.platform.document.OpenedDocument
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
        loader = { index ->
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

    private val store = ViewModelStore()
    private lateinit var viewModel: DocumentViewerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = DocumentViewerViewModel(opener) as T
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
