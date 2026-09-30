package com.kosmos.app.platform.share

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.logging.AppLogger

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
 * 2. 그 외에는 [Constants.MAX_ATTACHED_DOC_CHARS] + 1 자만 읽어 절단 여부를 판정하고 [SharedInput.Document].
 * 3. 실패(예외·null 스트림)는 `input = null` — 호출자가 안내를 띄운다.
 *
 * [WHY] ChatScreen 컴포저블 안에 있던 65줄을 옮겼다 — 화면 파일에서 I/O 를 떼어 내고 JVM 테스트가
 * 가능해진다. **동기 호출은 그대로다**: 큰 읽기를 아예 하지 않는 것(메타데이터·캡 경계 읽기)이
 * 이 경로의 해법이고, IO 로 옮기면 프리뷰 갱신이 비동기가 되어 E2E 의 waitForIdle 과 경합했다
 * (전체 바이트는 전송 시점에 ChatViewModel 이 IO 에서 읽는다).
 */
class AttachmentReader(private val contentResolver: ContentResolver) {

    /** @property truncated 문서가 캡을 넘어 앞부분만 담겼다. */
    data class Result(val input: SharedInput?, val truncated: Boolean = false)

    fun read(uri: Uri): Result = try {
        if (contentResolver.getType(uri)?.startsWith("image/") == true) readImage(uri) else readDocument(uri)
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

        return Result(
            input = SharedInput.Document(uri = uri, fileName = fileName, textContent = textContent.take(cap)),
            truncated = textContent.length > cap
        )
    }

    private companion object {
        const val TAG = "AttachmentReader"
        const val DEFAULT_DOCUMENT_NAME = "document.txt"
    }
}
