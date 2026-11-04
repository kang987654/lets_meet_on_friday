package com.kosmos.app.platform.document

import android.net.Uri
import androidx.core.net.toUri
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.document.DocumentResult
import com.kosmos.app.domain.library.LibraryChunker
import com.kosmos.app.domain.library.LibraryDocument
import com.kosmos.app.domain.library.LibraryFailure
import com.kosmos.app.domain.library.LibraryLimits
import com.kosmos.app.domain.library.LibraryLine
import com.kosmos.app.domain.library.LibraryRepository
import com.kosmos.app.domain.library.LibraryStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LibraryIndexer]
 * 문서 보관함 등록·다시 색인을 실행합니다 (0.41.0 M3) — 파일을 열어 글자를 모두 뽑고, 조각으로 나눠 저장소에 넣는다. 모델을 쓰지 않는다.
 *
 * ### Architecture Context
 * - **Layer**: Platform (Document) — [DocumentOpener](파서), [LibraryRepository](저장), 도메인 [LibraryChunker].
 * - 앱 스코프 코루틴에서 돈다(화면을 떠나도 색인은 계속). [start] 는 문서 홈 ViewModel 이 처음 열릴 때 부른다(AGENTS §2-④ — init 에서 일하지 않는다).
 *
 * ### Key Flow
 * 1. [add]: 문서를 [LibraryStatus.INDEXING] 으로 넣고 색인을 띄운다. [reindex] 는 같은 문서로 다시.
 * 2. 색인: 형식별로 전부 읽는다 — 읽기 모드 블록 전체, 모든 시트, PDF 모든 쪽(쪽마다 진행률).
 * 3. 상한([LibraryLimits])을 넘거나 글자가 없으면 [LibraryFailure] 로 실패 처리, 아니면 조각을 한 트랜잭션으로 넣는다.
 *
 * [WHY] 색인은 한 번에 하나씩(뮤텍스) — 책 PDF 두 권을 동시에 열면 렌더러·파서 메모리가 겹친다. 기다리는 문서는 INDEXING 으로 보인다.
 * [WHY] 백그라운드 모델 로드 금지(AGENTS §2-⑥)와 무관하다 — CPU 파싱뿐이다. 다만 프로세스가 죽으면 INDEXING 으로 남으므로 [start] 가 실패로 돌린다.
 */
@Singleton
class LibraryIndexer @Inject constructor(
    private val opener: DocumentOpener,
    private val repository: LibraryRepository,
    private val embedder: LibraryEmbedder
) {
    /** 색인 중인 문서의 진행률 — 문서 id → (읽은 단위, 전체 단위). PDF 는 쪽, 시트는 시트 수, 그 외는 (0, 1) → (1, 1). */
    data class Progress(val done: Int, val total: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val lock = Mutex()
    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val progress: StateFlow<Map<String, Progress>> = _progress.asStateFlow()

    /** 앞 실행에서 색인 중에 멈춘 문서를 실패로 — 멱등. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val changed = repository.markInterrupted(LibraryFailure.INTERRUPTED.name)
            if (changed is AppResult.Success && changed.data > 0) AppLogger.w(TAG, "색인 중단 문서 ${changed.data}개를 실패로")
        }
    }

    /** 새 문서를 보관함에 넣고 색인한다. */
    fun add(uri: Uri, name: String, mimeType: String?, now: Long = System.currentTimeMillis()): Job {
        val document = LibraryDocument(UUID.randomUUID().toString(), uri.toString(), name, mimeType, now, LibraryStatus.INDEXING)
        return scope.launch {
            repository.upsert(document)
            index(document)
        }
    }

    /** 실패했거나 원본이 바뀐 문서를 다시 색인한다. */
    fun reindex(document: LibraryDocument): Job = scope.launch {
        val pending = document.copy(status = LibraryStatus.INDEXING, error = null)
        repository.upsert(pending)
        index(pending)
    }

    fun remove(documentId: String): Job = scope.launch { repository.remove(documentId) }

    internal suspend fun index(document: LibraryDocument) = lock.withLock {
        try {
            when (val lines = readLines(document)) {
                is Read.Fail -> repository.markFailed(document.id, lines.reason)
                is Read.Ok -> store(document, lines.lines)
            }
        } finally {
            _progress.update { it - document.id }
        }
    }

    private suspend fun store(document: LibraryDocument, lines: List<LibraryLine>) {
        val chars = lines.sumOf { (it as? LibraryLine.Body)?.text?.length ?: 0 }
        if (chars == 0) {
            repository.markFailed(document.id, LibraryFailure.NO_TEXT.name)
            return
        }
        if (chars > LibraryLimits.MAX_DOCUMENT_CHARS) {
            repository.markFailed(document.id, LibraryFailure.TOO_LARGE.name)
            return
        }
        val others = (repository.totalChars(excludeId = document.id) as? AppResult.Success)?.data ?: 0
        if (others + chars > LibraryLimits.MAX_TOTAL_CHARS) {
            repository.markFailed(document.id, LibraryFailure.LIBRARY_FULL.name)
            return
        }
        val chunks = LibraryChunker.chunk(document.name, lines)
        if (repository.replaceChunks(document.id, chunks, chars) is AppResult.Failure) {
            repository.markFailed(document.id, LibraryFailure.OPEN_FAILED.name)
            return
        }
        // [WHY] 등록은 사용자가 시작한 일 — 임베딩 모델이 있으면 이 문서의 뜻 벡터를 이어서 만든다(없으면 아무것도 안 함, 0.41.2 Q4).
        embedder.prepare(document.id)
    }

    private sealed interface Read {
        data class Ok(val lines: List<LibraryLine>) : Read
        data class Fail(val reason: String) : Read
    }

    private suspend fun readLines(document: LibraryDocument): Read {
        val opened = when (val result = opener.open(document.uri.toUri(), document.mimeType)) {
            is DocumentResult.Fail -> return Read.Fail("${LibraryFailure.OPEN_FAILED.name}:${result.error.name}")
            is DocumentResult.Ok -> result.value
        }
        return try {
            when (opened) {
                is OpenedDocument.Flow -> Read.Ok(LibraryChunker.fromFlow(opened.document.blocks))
                is OpenedDocument.Spreadsheet -> {
                    val sheets = opened.sheetNames.indices.mapNotNull { i ->
                        report(document.id, i, opened.sheetNames.size)
                        (opened.sheet(i) as? DocumentResult.Ok)?.value
                    }
                    Read.Ok(LibraryChunker.fromSheets(sheets))
                }
                is OpenedDocument.Pdf -> {
                    val count = opened.pages.pageCount
                    val pages = (0 until count).map { i ->
                        report(document.id, i, count)
                        opened.pages.text(i)
                    }
                    if (pages.all { it.isNullOrBlank() }) Read.Fail(LibraryFailure.NO_TEXT.name) else Read.Ok(LibraryChunker.fromPages(pages))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "색인 실패", e)
            Read.Fail(LibraryFailure.OPEN_FAILED.name)
        } finally {
            opened.close()
        }
    }

    private fun report(documentId: String, done: Int, total: Int) {
        _progress.update { it + (documentId to Progress(done, total)) }
    }

    private companion object {
        const val TAG = "LibraryIndexer"
    }
}
