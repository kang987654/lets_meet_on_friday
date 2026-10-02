package com.kosmos.app.platform.share

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.document.DocumentType

/**
 * [AttachmentReader]
 * 입력바 첨부 피커가 고른 URI 를 [SharedInput] 으로 읽습니다 — 이미지는 크기 메타데이터만, 문서는
 * 캡까지만 읽는다.
 *
 * ### Architecture Context
 * - **Layer**: Platform (Share)
 * - **Dependencies**: [ContentResolver]
 *
 * ### Key Flow
 * 1. MIME 이 `image/` 면 SIZE 메타데이터(없으면 `available()`)로 [SharedInput.Image].
 * 2. 문서 파일(xlsx·docx·hwpx·PDF)은 읽지 않고 `documentFile` 로 돌려준다 — 호출자가 비동기 추출(0.35.0).
 * 3. 그 외에는 [Constants.MAX_ATTACHED_DOC_CHARS] + 1 자만 읽어 절단 여부를 판정하고 [SharedInput.Document]. 바이너리면 거부.
 * 3. 실패(예외·null 스트림)는 `input = null` — 호출자가 안내를 띄운다.
 *
 * [WHY] ChatScreen 컴포저블 안에 있던 65줄을 옮겼다 — 화면 파일에서 I/O 를 떼어 내고 JVM 테스트가
 * 가능해진다. **동기 호출은 그대로다**: 큰 읽기를 아예 하지 않는 것(메타데이터·캡 경계 읽기)이
 * 이 경로의 해법이고, IO 로 옮기면 프리뷰 갱신이 비동기가 되어 E2E 의 waitForIdle 과 경합했다
 * (전체 바이트는 전송 시점에 ChatViewModel 이 IO 에서 읽는다).
 */
class AttachmentReader(private val contentResolver: ContentResolver) {

    /**
     * @property truncated 문서가 캡을 넘어 앞부분만 담겼다.
     * @property documentFile 문서 파일(xlsx·docx·hwpx·PDF) — 여기서 읽지 않고 호출자가 비동기 추출로 넘긴다(0.35.0).
     * @property rejected 글자로 읽을 수 없는 파일(xls·hwp·zip 등) — 깨진 바이트를 모델에 넣지 않으려고 거부한다.
     */
    data class Result(
        val input: SharedInput?,
        val truncated: Boolean = false,
        val documentFile: Boolean = false,
        val rejected: Boolean = false
    )

    fun read(uri: Uri): Result = try {
        val mime = contentResolver.getType(uri)
        when {
            mime?.startsWith("image/") == true -> readImage(uri)
            isDocumentFile(mime, uri) -> Result(input = null, documentFile = true)
            else -> readDocument(uri)
        }
    } catch (e: Exception) {
        AppLogger.e(TAG, "첨부 읽기 실패", e)
        Result(null)
    }

    private fun readImage(uri: Uri): Result {
        val sizeBytes = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && idx != -1 && !cursor.isNull(idx)) cursor.getLong(idx) else null
            }
            ?: contentResolver.openInputStream(uri)?.use { it.available().toLong() }
        return Result(sizeBytes?.let { SharedInput.Image(uri = uri, sizeBytes = it) })
    }

    private fun readDocument(uri: Uri): Result {
        // [WHY] 캡은 Constants.MAX_ATTACHED_DOC_CHARS 로 예산에서 파생된다. 예전 2500 은 예산 6000
        // 시절의 유물 — 그대로 두면 그 턴의 KV 가 GPU 숫자 깨짐 발병점을 넘고, 다음 턴부터는 슬라이딩
        // 윈도우에서 통째로 탈락해 모델이 문서를 본 적 없는 상태가 됐다. +1 은 절단 여부 감지용이다.
        val cap = Constants.MAX_ATTACHED_DOC_CHARS
        val textContent = contentResolver.openInputStream(uri)?.use { inputStream ->
            val buffer = CharArray(cap + 1)
            val read = inputStream.bufferedReader().read(buffer, 0, buffer.size)
            if (read <= 0) "" else String(buffer, 0, read)
        } ?: return Result(null)

        val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) cursor.getString(nameIndex) else null
        } ?: DEFAULT_DOCUMENT_NAME

        // [WHY] 글자 파일이 아니면 거부한다 — 예전에는 xls·hwp·zip 바이트가 깨진 문자열로 모델에 들어갔다(0.35.0 M4).
        if (looksBinary(textContent)) return Result(input = null, rejected = true)

        return Result(
            input = SharedInput.Document(uri = uri, fileName = fileName, textContent = textContent.take(cap)),
            truncated = textContent.length > cap
        )
    }

    private fun isDocumentFile(mime: String?, uri: Uri): Boolean {
        val type = DocumentType.detect(mime, displayName(uri))
        return type == DocumentType.XLSX || type == DocumentType.DOCX || type == DocumentType.HWPX || type == DocumentType.PDF
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx != -1) cursor.getString(idx) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    internal companion object {
        const val TAG = "AttachmentReader"
        const val DEFAULT_DOCUMENT_NAME = "document.txt"

        /** NUL 이 있거나 깨진 글자(U+FFFD)가 10% 를 넘으면 바이너리로 본다. */
        fun looksBinary(text: String): Boolean {
            if (text.isEmpty()) return false
            if ('\u0000' in text) return true
            return text.count { it == '\uFFFD' } * 10 > text.length
        }
    }

}
