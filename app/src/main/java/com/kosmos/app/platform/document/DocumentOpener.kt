package com.kosmos.app.platform.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.document.CsvReader
import com.kosmos.app.domain.document.DocumentError
import com.kosmos.app.domain.document.DocumentException
import com.kosmos.app.domain.document.DocumentLimits
import com.kosmos.app.domain.document.DocumentResult
import com.kosmos.app.domain.document.DocumentSniffer
import com.kosmos.app.domain.document.DocumentType
import com.kosmos.app.domain.document.Sheet
import com.kosmos.app.domain.document.XlsxReader
import com.kosmos.app.domain.document.ZipSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipFile
import javax.inject.Inject

/** 뷰어가 연 문서 — 닫으면 캐시 복사본과 렌더러를 놓는다. */
sealed interface OpenedDocument : Closeable {
    val fileName: String

    /** xlsx·csv. 시트는 [sheet] 를 부를 때 하나씩 읽는다. 큰 시트는 앞부분을 [onFirstRows] 로 먼저 준다. */
    class Spreadsheet(
        override val fileName: String,
        val sheetNames: List<String>,
        private val loader: suspend (index: Int, onFirstRows: (Sheet) -> Unit) -> DocumentResult<Sheet>,
        private val onClose: () -> Unit = {}
    ) : OpenedDocument {
        suspend fun sheet(index: Int, onFirstRows: (Sheet) -> Unit = {}): DocumentResult<Sheet> = loader(index, onFirstRows)
        override fun close() = onClose()
    }

    class Pdf(override val fileName: String, val pages: PdfPages) : OpenedDocument {
        override fun close() = pages.close()
    }
}

/** PDF 페이지 렌더 — 테스트가 가짜로 바꿀 수 있게 인터페이스로 둔다. */
interface PdfPages : Closeable {
    val pageCount: Int

    /** 높이 / 너비. 페이지를 못 읽으면 A4 비율. */
    fun aspectRatio(index: Int): Float

    /** 화면 폭 [widthPx] 로 그린 페이지. 실패하면 null. */
    suspend fun render(index: Int, widthPx: Int): Bitmap?
}

/**
 * [DocumentOpener]
 * 콘텐츠 URI 를 형식에 맞게 엽니다 — 뷰어 ViewModel 의 유일한 의존(모델·DB 없음, 0.34.0 고정 제약).
 */
interface DocumentOpener {
    suspend fun open(uri: Uri, mimeTypeHint: String? = null): DocumentResult<OpenedDocument>
}

/**
 * [AndroidDocumentOpener]
 * [DocumentOpener] 구현 — 파일을 앱 캐시로 복사한 뒤 형식별 리더에 넘긴다.
 *
 * ### Architecture Context
 * - **Layer**: Platform (Document)
 * - **Dependencies**: ContentResolver, `domain.document` 리더들, [PdfRenderer]
 *
 * ### Key Flow
 * 1. 이름·MIME 조회 → [DocumentType.detect].
 * 2. xlsx·PDF 는 캐시로 복사(크기 상한) — xlsx 는 `ZipFile` 무작위 접근, PDF 는 탐색 가능한 파일 기술자가 필요하다.
 * 3. csv 는 스트림 그대로 [CsvReader].
 *
 * [WHY] 복사하는 이유 — 메신저·메일 앱의 콘텐츠 URI 는 파이프 기반 기술자를 주는 경우가 있어 `PdfRenderer`(탐색 필요)가
 * 실패하고, `ZipInputStream` 순차 읽기로는 시트 뒤에 저장된 공유 문자열 표를 먼저 읽을 수 없다(XlsxReader [WHY]).
 * 복사본은 닫을 때 지우고, 남은 것(비정상 종료)은 다음 열기 때 하루가 지난 것만 지운다 — 다른 문서 창이 쓰는 중일 수 있다.
 */
class AndroidDocumentOpener @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DocumentOpener {

    private val limits = DocumentLimits()

    override suspend fun open(uri: Uri, mimeTypeHint: String?): DocumentResult<OpenedDocument> = withContext(Dispatchers.IO) {
        try {
            // [WHY] 표시 이름이 없는 file:// URI 는 마지막 경로 조각을 쓰는데, 경로 구분자가 섞여 오는 경우(백슬래시 경로 등)가
            // 있어 마지막 조각만 남긴다 — 상단 바에 전체 경로가 보이지 않게.
            val name = (displayName(uri) ?: uri.lastPathSegment)
                ?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() } ?: "문서"
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: mimeTypeHint
            when (DocumentType.detect(mime, name)) {
                DocumentType.XLSX -> openXlsx(uri, name)
                DocumentType.CSV -> openCsv(uri, name)
                DocumentType.PDF -> openPdf(uri, name)
                DocumentType.UNSUPPORTED -> DocumentResult.Fail(DocumentError.UNSUPPORTED)
            }
        } catch (e: DocumentException) {
            DocumentResult.Fail(e.error)
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "문서 권한 없음: ${e.message}")
            DocumentResult.Fail(DocumentError.UNREADABLE)
        } catch (e: FileNotFoundException) {
            DocumentResult.Fail(DocumentError.UNREADABLE)
        } catch (e: java.io.IOException) {
            AppLogger.w(TAG, "문서 읽기 실패: ${e.message}")
            DocumentResult.Fail(DocumentError.CORRUPT)
        }
    }

    private fun displayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx != -1) cursor.getString(idx) else null
            }
        }.getOrNull()

    private fun openCsv(uri: Uri, name: String): DocumentResult<OpenedDocument> {
        val input = context.contentResolver.openInputStream(uri) ?: return DocumentResult.Fail(DocumentError.UNREADABLE)
        return when (val sheet = CsvReader(limits).read(input, name)) {
            is DocumentResult.Ok -> DocumentResult.Ok(OpenedDocument.Spreadsheet(name, listOf(name), { _, _ -> DocumentResult.Ok(sheet.value) }))
            is DocumentResult.Fail -> sheet
        }
    }

    private fun openXlsx(uri: Uri, name: String): DocumentResult<OpenedDocument> {
        val file = copyToCache(uri)
        val head = file.inputStream().use { it.readNBytesCompat(8) }
        if (!DocumentSniffer.isZip(head)) {
            file.delete()
            // [WHY] 암호 걸린 xlsx 는 OLE 복합 파일로 저장된다(옛 xls 와 같은 겉모양) — "손상됨"이 아니라 원인을 알려 준다.
            return DocumentResult.Fail(if (DocumentSniffer.isOleCompound(head)) DocumentError.ENCRYPTED else DocumentError.CORRUPT)
        }
        val zip = try {
            ZipFile(file)
        } catch (e: java.io.IOException) {
            file.delete()
            return DocumentResult.Fail(DocumentError.CORRUPT)
        }
        val close = { runCatching { zip.close() }; file.delete(); Unit }
        val source = ZipSource { entry -> zip.getEntry(entry)?.let { zip.getInputStream(it) } }
        val reader = XlsxReader(source, limits)
        return when (val workbook = reader.open()) {
            is DocumentResult.Fail -> { close(); workbook }
            is DocumentResult.Ok -> {
                // [WHY] ZipFile 은 동시 읽기에 안전하지 않은 구현이 있다 — 시트 읽기를 직렬화한다.
                val lock = Mutex()
                DocumentResult.Ok(
                    OpenedDocument.Spreadsheet(
                        fileName = name,
                        sheetNames = workbook.value.sheetNames,
                        loader = { index, onFirstRows ->
                            lock.withLock { withContext(Dispatchers.IO) { reader.readSheet(workbook.value, index, onFirstRows) } }
                        },
                        onClose = close
                    )
                )
            }
        }
    }

    private fun openPdf(uri: Uri, name: String): DocumentResult<OpenedDocument> {
        val file = copyToCache(uri)
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (e: SecurityException) {
            descriptor.close(); file.delete()
            return DocumentResult.Fail(DocumentError.ENCRYPTED)
        } catch (e: java.io.IOException) {
            descriptor.close(); file.delete()
            return DocumentResult.Fail(DocumentError.CORRUPT)
        }
        return DocumentResult.Ok(OpenedDocument.Pdf(name, AndroidPdfPages(renderer, descriptor, file)))
    }

    private fun copyToCache(uri: Uri): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val dayAgo = System.currentTimeMillis() - STALE_MS
        dir.listFiles()?.filter { it.lastModified() < dayAgo }?.forEach { it.delete() }
        val target = File.createTempFile("doc", ".bin", dir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
            input.use { source ->
                target.outputStream().use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_FILE_BYTES) throw DocumentException(DocumentError.TOO_LARGE)
                        sink.write(buffer, 0, n)
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        return target
    }

    private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
        val out = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = read(out, read, n - read)
            if (r < 0) break
            read += r
        }
        return out.copyOf(read)
    }

    private companion object {
        const val TAG = "DocumentOpener"
        const val CACHE_DIR = "documents"
        const val STALE_MS = 24L * 60 * 60 * 1000
        const val MAX_FILE_BYTES = 100L * 1024 * 1024
    }
}

/**
 * [AndroidPdfPages]
 * [PdfRenderer] 래퍼 — 페이지는 한 번에 하나만 열 수 있어(동시 `openPage` 는 IllegalStateException) 렌더를 직렬화하고,
 * 그린 비트맵은 [LruCache] 로 앱 힙의 1/8 까지 둔다.
 */
private class AndroidPdfPages(
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor,
    private val file: File
) : PdfPages {
    private val lock = Mutex()
    private var closed = false

    override val pageCount: Int = renderer.pageCount

    // [WHY] 페이지 크기는 열어 봐야 안다 — 목록 자리 높이를 미리 잡으려고 열 때 한 번 읽는다(쪽마다 수 μs, 메타데이터만).
    private val ratios: FloatArray = FloatArray(minOf(pageCount, MAX_MEASURED_PAGES)) { index ->
        renderer.openPage(index).use { page -> if (page.width > 0) page.height.toFloat() / page.width else A4 }
    }

    private val cache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    override fun aspectRatio(index: Int): Float = ratios.getOrElse(index) { A4 }

    override suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        if (closed || index !in 0 until pageCount || widthPx <= 0) return@withLock null
        cache.get(index)?.takeIf { it.width == widthPx }?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            runCatching {
                renderer.openPage(index).use { page ->
                    val height = (widthPx * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                    androidx.core.graphics.createBitmap(widthPx, height).also { bitmap ->
                        bitmap.eraseColor(Color.WHITE) // 투명 배경 PDF 가 다크 모드에서 검게 보이지 않게
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }.onFailure { AppLogger.w("PdfPages", "페이지 $index 렌더 실패: ${it.message}") }.getOrNull()
        }?.also { cache.put(index, it) }
    }

    override fun close() {
        // [WHY] 렌더 중에 닫으면 네이티브 크래시 — 렌더와 같은 잠금 뒤에서 닫는다(뷰모델 onCleared 는 비중단 문맥이라 별도 코루틴).
        CoroutineScope(Dispatchers.IO).launch { lock.withLock { closeNow() } }
    }

    private fun closeNow() {
        if (closed) return
        closed = true
        cache.evictAll()
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        file.delete()
    }

    private companion object {
        const val A4 = 1.4142f
        const val MAX_MEASURED_PAGES = 2000
    }
}
