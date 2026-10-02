package com.kosmos.app.platform.document

import android.net.Uri
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.document.ClippedText
import com.kosmos.app.domain.document.DocumentError
import com.kosmos.app.domain.document.DocumentResult
import com.kosmos.app.domain.document.DocumentText
import javax.inject.Inject

/**
 * [DocumentTextExtractor]
 * 채팅 입력바에서 첨부한 문서 파일(xlsx·docx·hwpx·PDF)을 채팅 첨부 글자로 바꿉니다 (0.35.0 M4).
 *
 * ### Architecture Context
 * - **Layer**: Platform (Document)
 * - **Dependencies**: [DocumentOpener], [DocumentText](exp46 형식·행 경계 자르기)
 *
 * ### Key Flow
 * 1. 뷰어와 같은 열기 경로로 문서를 연다.
 * 2. 시트는 첫 시트 전체, 읽기 모드는 문서 전체, PDF 는 첫 페이지 글자(꺼낼 수 있는 기기만) — 상한까지 앞부분.
 * 3. 다 쓰면 닫는다(캐시 복사본 삭제).
 *
 * [WHY] 문서 파일은 압축·바이너리라 글자로 읽으면 깨진 바이트가 모델에 들어간다 — 파싱해서 글자로 바꾼다. 범위를 고르려면
 * 뷰어의 "채팅으로"를 쓴다 — 여기서는 앞부분만 보낸다.
 */
class DocumentTextExtractor @Inject constructor(
    private val opener: DocumentOpener
) {
    /** @return 파일 이름과 첨부 글자. */
    suspend fun extract(uri: Uri, mimeType: String?): DocumentResult<Pair<String, ClippedText>> {
        val opened = when (val result = opener.open(uri, mimeType)) {
            is DocumentResult.Fail -> return result
            is DocumentResult.Ok -> result.value
        }
        return try {
            val text = when (opened) {
                is OpenedDocument.Spreadsheet -> when (val sheet = opened.sheet(0)) {
                    is DocumentResult.Fail -> return sheet
                    is DocumentResult.Ok -> DocumentText.sheet(sheet.value, 0..Int.MAX_VALUE, CAP)
                }
                is OpenedDocument.Flow -> DocumentText.flow(opened.document.blocks, CAP)
                is OpenedDocument.Pdf -> opened.pages.text(0)?.let { DocumentText.plain(it, CAP) }
                    ?: return DocumentResult.Fail(DocumentError.UNSUPPORTED)
            }
            if (text.text.isBlank()) DocumentResult.Fail(DocumentError.UNSUPPORTED) else DocumentResult.Ok(opened.fileName to text)
        } finally {
            opened.close()
        }
    }

    private companion object {
        val CAP = Constants.MAX_ATTACHED_DOC_CHARS
    }
}
