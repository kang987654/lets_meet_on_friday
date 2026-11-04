package com.kosmos.app.feature.chat.result

import android.content.Context
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.R
import com.kosmos.app.assistant.tool.SearchDocumentsToolExecutor
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.library.LibraryRepository
import com.kosmos.app.domain.library.LibraryStatus
import com.kosmos.app.domain.model.MessageResult
import com.kosmos.app.feature.document.DocumentViewerActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/**
 * [DocSourceViewModel]
 * 출처 카드(문서 검색 결과, 0.41.2 M4)를 누르면 그 문서를 뷰어의 그 위치로 엽니다.
 *
 * ### Architecture Context
 * - **Layer**: Feature / Presentation (Chat) — [LibraryRepository](문서 URI·상태).
 *
 * [WHY] ChatExtrasViewModel 과 나눈다 — 그 생성자를 늘리면 화면 카탈로그·테스트의 손 생성부를 모두 고쳐야 한다.
 * [WHY] 원본은 앱 밖에 그대로 있다(0.41.0 D2) — 지웠거나 권한이 사라졌으면 뷰어가 아니라 "원본을 찾을 수 없음"을 알린다.
 */
@HiltViewModel
class DocSourceViewModel @Inject constructor(
    private val library: LibraryRepository
) : ViewModel() {

    /** [result] 의 첫 출처를 연다. */
    fun open(context: Context, result: MessageResult) {
        if (result.kind != MessageResult.Kind.DOC_SOURCE) return
        val source = firstSource(result.meta)
        viewModelScope.launch {
            val doc = (library.document(result.ref) as? AppResult.Success)?.data
            if (doc == null || doc.status != LibraryStatus.READY) {
                Toast.makeText(context, R.string.result_doc_source_missing, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val intent = DocumentViewerActivity.intent(context, doc.uri.toUri(), doc.mimeType, page = source?.page, find = source?.section)
            runCatching { context.startActivity(intent) }
                .onFailure { Toast.makeText(context, R.string.result_doc_source_missing, Toast.LENGTH_SHORT).show() }
        }
    }

    /** 출처 meta 의 첫 항목 — 위치(쪽·머리말). */
    data class Source(val section: String?, val page: Int?)

    companion object {
        fun firstSource(meta: String?): Source? {
            val first = meta?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?.optJSONArray(SearchDocumentsToolExecutor.META_SOURCES)?.optJSONObject(0) ?: return null
            return Source(
                section = first.optString(SearchDocumentsToolExecutor.META_SECTION).takeIf { it.isNotEmpty() },
                page = first.optInt(SearchDocumentsToolExecutor.META_PAGE, 0).takeIf { it > 0 }
            )
        }
    }
}
