package com.kosmos.app.feature.document

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.document.DocumentError
import com.kosmos.app.domain.document.DocumentResult
import com.kosmos.app.domain.document.Sheet
import com.kosmos.app.platform.document.DocumentOpener
import com.kosmos.app.platform.document.OpenedDocument
import com.kosmos.app.platform.document.PdfPages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    data class Failed(val error: DocumentError) : DocumentViewerState
}

/**
 * [DocumentViewerViewModel]
 * 문서 하나를 열고 시트 전환을 맡습니다 (0.34.0).
 *
 * ### Architecture Context
 * - **Layer**: Feature (Document)
 * - **Dependencies**: [DocumentOpener] 하나 — **모델·DB 를 주입하지 않는다**(뷰어는 모델 없이 열린다, 계획서 고정 제약)
 *
 * ### Key Flow
 * 1. [load] — 같은 URI 면 다시 열지 않는다(화면 회전·재구성).
 * 2. 스프레드시트는 첫 시트만 읽어 보여 주고, 다른 시트는 [selectSheet] 때 읽는다.
 * 3. [onCleared] 에서 문서를 닫는다(캐시 복사본 삭제·렌더러 해제).
 */
@HiltViewModel
class DocumentViewerViewModel @Inject constructor(
    private val opener: DocumentOpener
) : ViewModel() {

    private val _state = MutableStateFlow<DocumentViewerState>(DocumentViewerState.Loading)
    val state: StateFlow<DocumentViewerState> = _state.asStateFlow()

    private var loadedUri: Uri? = null
    private var document: OpenedDocument? = null
    private var sheetJob: Job? = null

    fun load(uri: Uri, mimeTypeHint: String? = null) {
        if (uri == loadedUri) return
        loadedUri = uri
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
    }
}
