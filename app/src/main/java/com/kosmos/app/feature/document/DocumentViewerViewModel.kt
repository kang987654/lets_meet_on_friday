package com.kosmos.app.feature.document

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.document.ClippedText
import com.kosmos.app.domain.document.DocumentError
import com.kosmos.app.domain.document.DocumentText
import com.kosmos.app.domain.document.FlowDocument
import com.kosmos.app.domain.document.DocumentResult
import com.kosmos.app.domain.document.Sheet
import com.kosmos.app.platform.document.DocumentOpener
import com.kosmos.app.platform.document.OpenedDocument
import com.kosmos.app.platform.document.PdfPages
import com.kosmos.app.platform.share.ShareIntentHandler
import com.kosmos.app.platform.share.SharedInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 뷰어 화면 상태. */
sealed interface DocumentViewerState {
    data object Loading : DocumentViewerState

    /**
     * @property sheet 선택한 시트 — 읽는 중이면 null.
     * @property sheetError 선택한 시트만 못 읽었을 때(다른 시트는 볼 수 있다).
     */
    data class Spreadsheet(
        val fileName: String,
        val sheetNames: List<String>,
        val selected: Int = 0,
        val sheet: Sheet? = null,
        val sheetError: DocumentError? = null
    ) : DocumentViewerState

    data class Pdf(val fileName: String, val pages: PdfPages) : DocumentViewerState

    /** docx·hwpx 읽기 모드. [images] 는 zip 안 이미지를 화면 폭에 맞춰 읽는다. */
    data class Flow(
        val fileName: String,
        val document: FlowDocument,
        val images: suspend (entryName: String, widthPx: Int) -> Bitmap?
    ) : DocumentViewerState

    data class Failed(val error: DocumentError) : DocumentViewerState
}

/** 연 문서의 파일 이름 — 읽는 중·실패면 null. */
val DocumentViewerState.fileName: String?
    get() = when (this) {
        is DocumentViewerState.Spreadsheet -> fileName
        is DocumentViewerState.Pdf -> fileName
        is DocumentViewerState.Flow -> fileName
        DocumentViewerState.Loading, is DocumentViewerState.Failed -> null
    }

/**
 * "채팅으로 보내기" 고르는 중의 상태 (0.35.0 M3).
 *
 * @property preview 지금 고른 범위를 채팅 첨부 글자로 바꾼 것 — null 이면 아직 고른 것이 없다.
 */
sealed interface ChatSelection {
    val preview: ClippedText?

    /** 시트 행 범위 — 행 번호를 두 번 눌러 시작·끝을 정한다. */
    data class Rows(val anchor: Int? = null, val end: Int? = null, override val preview: ClippedText? = null) : ChatSelection {
        val range: IntRange? get() = if (anchor == null || end == null) null else minOf(anchor, end)..maxOf(anchor, end)
    }

    /** 읽기 모드 블록 — 눌러서 넣고 빼기. */
    data class Blocks(val indices: Set<Int> = emptySet(), override val preview: ClippedText? = null) : ChatSelection

    /** PDF 현재 페이지. [unsupported] 면 이 기기는 PDF 글자를 꺼낼 수 없다(안드로이드 15 미만). */
    data class PdfPage(val page: Int = 0, override val preview: ClippedText? = null, val unsupported: Boolean = false) : ChatSelection
}

/**
 * [DocumentViewerViewModel]
 * 문서 하나를 열고 시트 전환을 맡습니다 (0.34.0).
 *
 * ### Architecture Context
 * - **Layer**: Feature (Document)
 * - **Dependencies**: [DocumentOpener], [SettingsDataStore](글자 크기), [ShareIntentHandler](채팅으로 넘기기) — **모델·DB 를 주입하지 않는다**(뷰어는 모델 없이 열린다, 계획서 고정 제약)
 *
 * ### Key Flow
 * 1. [load] — 같은 URI 면 다시 열지 않는다(화면 회전·재구성).
 * 2. 스프레드시트는 첫 시트만 읽어 보여 주고, 다른 시트는 [selectSheet] 때 읽는다.
 * 3. [onCleared] 에서 문서를 닫는다(캐시 복사본 삭제·렌더러 해제).
 * 4. 채팅으로 보내기(0.35.0): [startSelection] → 범위 고르기 → [sendToChat] 이 [ShareIntentHandler] 에 첨부를 넘긴다.
 */
@HiltViewModel
class DocumentViewerViewModel @Inject constructor(
    private val opener: DocumentOpener,
    private val settings: SettingsDataStore,
    private val shareHandler: ShareIntentHandler
) : ViewModel() {

    private val _selection = MutableStateFlow<ChatSelection?>(null)

    // 화면에 보이는 PDF 페이지 — 고르기를 시작하면 이 페이지부터.
    private var pdfPage = 0

    /** null 이면 고르는 중이 아니다. */
    val selection: StateFlow<ChatSelection?> = _selection.asStateFlow()

    /** 채팅으로 보낼 범위를 고르기 시작한다. 읽는 중·실패 상태에서는 무시. */
    fun startSelection() {
        when (val s = _state.value) {
            is DocumentViewerState.Spreadsheet -> _selection.value = if (s.sheet == null) null else ChatSelection.Rows()
            is DocumentViewerState.Flow -> _selection.value = ChatSelection.Blocks()
            // [WHY] 대입하지 않고 loadPdfPage 에 맡긴다 — 바깥에서 대입하면 즉시 끝난 글자 읽기 결과를 빈 상태로 덮어쓴다.
            is DocumentViewerState.Pdf -> loadPdfPage(s, pdfPage)
            else -> _selection.value = null
        }
    }

    fun cancelSelection() {
        _selection.value = null
    }

    /** 행 번호 탭 — 첫 탭은 시작, 둘째 탭은 끝, 범위가 정해진 뒤의 탭은 새로 시작. */
    fun tapRow(row: Int) {
        val current = _selection.value as? ChatSelection.Rows ?: return
        val sheet = (_state.value as? DocumentViewerState.Spreadsheet)?.sheet ?: return
        val next = if (current.anchor != null && current.end == current.anchor) current.copy(end = row) else ChatSelection.Rows(row, row)
        _selection.value = next.copy(preview = next.range?.let { DocumentText.sheet(sheet, it, CAP) })
    }

    fun tapBlock(index: Int) {
        val current = _selection.value as? ChatSelection.Blocks ?: return
        val blocks = (_state.value as? DocumentViewerState.Flow)?.document?.blocks ?: return
        val indices = if (index in current.indices) current.indices - index else current.indices + index
        val picked = indices.sorted().mapNotNull { blocks.getOrNull(it) }
        _selection.value = ChatSelection.Blocks(indices, if (picked.isEmpty()) null else DocumentText.flow(picked, CAP))
    }

    /** PDF 를 넘기면 보낼 페이지도 따라간다. */
    fun onPdfPage(page: Int) {
        pdfPage = page
        val current = _selection.value as? ChatSelection.PdfPage ?: return
        val pdf = _state.value as? DocumentViewerState.Pdf ?: return
        if (page != current.page) loadPdfPage(pdf, page)
    }

    private fun loadPdfPage(pdf: DocumentViewerState.Pdf, page: Int) {
        _selection.value = ChatSelection.PdfPage(page)
        viewModelScope.launch {
            val text = pdf.pages.text(page)
            _selection.update { s ->
                if (s !is ChatSelection.PdfPage || s.page != page) return@update s
                if (text == null) s.copy(unsupported = true) else s.copy(preview = DocumentText.plain(text, CAP).takeIf { it.text.isNotBlank() })
            }
        }
    }

    /**
     * 고른 범위를 채팅 입력바 첨부로 넘긴다. 넘겼으면 true — 화면이 채팅으로 간다(그때 모델이 켜진다, ADR-027).
     *
     * [WHY] 인텐트 extra 가 아니라 같은 프로세스의 [ShareIntentHandler] 로 넘긴다 — MainActivity 는 exported 라 다른 앱도 명시적
     * 인텐트를 보낼 수 있다. 메모리 안 전달이면 외부에서 임의 글자를 "첨부 문서"로 주입할 경로가 처음부터 없다.
     */
    fun sendToChat(): Boolean {
        val uri = loadedUri ?: return false
        val text = _selection.value?.preview?.text?.takeIf { it.isNotBlank() } ?: return false
        val name = _state.value.fileName ?: return false
        shareHandler.offer(SharedInput.Document(uri = uri, fileName = name, textContent = text))
        _selection.value = null
        return true
    }

    /** 읽기 모드 글자 크기 단계(0~2) — 기기에 기억한다(설정 DataStore, DB 아님). */
    val textScaleStep: StateFlow<Int> = settings.docTextScaleStepFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    fun changeTextScale(delta: Int) {
        viewModelScope.launch { settings.saveDocTextScaleStep(textScaleStep.value + delta) }
    }

    private val _state = MutableStateFlow<DocumentViewerState>(DocumentViewerState.Loading)
    val state: StateFlow<DocumentViewerState> = _state.asStateFlow()

    private var loadedUri: Uri? = null
    private var document: OpenedDocument? = null
    private var sheetJob: Job? = null

    fun load(uri: Uri, mimeTypeHint: String? = null) {
        if (uri == loadedUri) return
        loadedUri = uri
        _selection.value = null
        pdfPage = 0
        document?.close()
        document = null
        _state.value = DocumentViewerState.Loading
        viewModelScope.launch {
            val started = System.nanoTime()
            val result = opener.open(uri, mimeTypeHint)
            // [WHY] 첫 표시 시간은 실기기 게이트 항목이다 — 측정용 로그를 남긴다(계획서 M4).
            AppLogger.d(TAG, "열기 ${(System.nanoTime() - started) / 1_000_000}ms — ${result::class.simpleName}")
            when (result) {
                is DocumentResult.Fail -> _state.value = DocumentViewerState.Failed(result.error)
                is DocumentResult.Ok -> {
                    val opened = result.value
                    document = opened
                    when (opened) {
                        is OpenedDocument.Pdf -> _state.value = DocumentViewerState.Pdf(opened.fileName, opened.pages)
                        is OpenedDocument.Flow -> _state.value = DocumentViewerState.Flow(opened.fileName, opened.document, opened::image)
                        is OpenedDocument.Spreadsheet -> {
                            _state.value = DocumentViewerState.Spreadsheet(opened.fileName, opened.sheetNames)
                            readSheet(opened, 0)
                        }
                    }
                }
            }
        }
    }

    fun selectSheet(index: Int) {
        val current = _state.value as? DocumentViewerState.Spreadsheet ?: return
        _selection.value = null // 고른 행은 시트에 묶여 있다
        val opened = document as? OpenedDocument.Spreadsheet ?: return
        if (index == current.selected || index !in current.sheetNames.indices) return
        _state.value = current.copy(selected = index, sheet = null, sheetError = null)
        readSheet(opened, index)
    }

    private fun readSheet(opened: OpenedDocument.Spreadsheet, index: Int) {
        sheetJob?.cancel()
        sheetJob = viewModelScope.launch {
            val started = System.nanoTime()
            val result = opened.sheet(index) { partial ->
                AppLogger.d(TAG, "시트 $index 앞부분 ${(System.nanoTime() - started) / 1_000_000}ms — ${partial.rows.size}행")
                _state.update { s -> if (s is DocumentViewerState.Spreadsheet && s.selected == index && s.sheet == null) s.copy(sheet = partial) else s }
            }
            AppLogger.d(TAG, "시트 $index 읽기 ${(System.nanoTime() - started) / 1_000_000}ms — ${(result as? DocumentResult.Ok)?.value?.rows?.size ?: result}행")
            _state.update { s ->
                if (s !is DocumentViewerState.Spreadsheet || s.selected != index) return@update s
                when (result) {
                    is DocumentResult.Ok -> s.copy(sheet = result.value, sheetError = null)
                    is DocumentResult.Fail -> s.copy(sheet = null, sheetError = result.error)
                }
            }
        }
    }

    override fun onCleared() {
        document?.close()
        document = null
    }

    private companion object {
        const val TAG = "DocumentViewer"
        val CAP = Constants.MAX_ATTACHED_DOC_CHARS
    }
}
