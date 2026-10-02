package com.kosmos.app.domain.document

/**
 * [DocumentType]
 * 문서 뷰어가 여는 형식과 판별 규칙입니다 (0.34.0).
 *
 * ### Key Flow
 * 1. MIME 이 알려진 형식이면 그것을 쓴다.
 * 2. 아니면(없음·octet-stream·엉뚱한 MIME) 파일 확장자로 판별한다.
 *
 * [WHY] 확장자 폴백이 필요한 이유 — 파일 앱·메신저가 MIME 을 `application/octet-stream` 으로 보내거나, csv 를
 * Windows 관례대로 `application/vnd.ms-excel` 로 보내는 경우가 흔하다. MIME 만 믿으면 열 수 있는 파일을 거부한다.
 */
enum class DocumentType {
    PDF, XLSX, CSV, DOCX, HWPX, UNSUPPORTED;

    companion object {
        /** 뷰어 "연결 프로그램" 필터·파일 선택기에 쓰는 MIME 목록 — 매니페스트와 같게 유지한다. */
        val VIEWABLE_MIME_TYPES: List<String> = listOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv",
            "text/comma-separated-values",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            // [WHY] hwpx MIME 은 표준이 하나로 정해지지 않았다 — 한컴 등록값과 기기·앱별 변종을 함께 받는다(0.35.0, 실기기 확인 항목).
            "application/hwp+zip",
            "application/haansofthwpx",
            "application/vnd.hancom.hwpx"
        )

        /**
         * 앱 안 파일 선택기(`OpenDocument`)에 넘기는 MIME — 뷰어 필터에 `application/octet-stream` 을 더한다.
         *
         * [WHY] 기기가 hwpx 를 모르면 MIME 이 octet-stream 이 되어(0.35.0 에뮬레이터 실측) 선택기에서 고를 수조차 없다. 선택기는
         * 사용자가 직접 고르는 곳이라 넓혀도 스팸이 없고, 연 뒤 확장자로 판별해 모르는 형식은 안내로 끝난다. "연결 프로그램" 필터는
         * 모든 바이너리에 앱이 뜨므로 넓히지 않는다(0.34.0 D4).
         */
        val PICKER_MIME_TYPES: List<String> = VIEWABLE_MIME_TYPES + "application/octet-stream"

        private val BY_MIME = mapOf(
            "application/pdf" to PDF,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to XLSX,
            "text/csv" to CSV,
            "text/comma-separated-values" to CSV,
            "application/csv" to CSV,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to DOCX,
            "application/hwp+zip" to HWPX,
            "application/haansofthwpx" to HWPX,
            "application/vnd.hancom.hwpx" to HWPX
        )

        private val BY_EXTENSION = mapOf("pdf" to PDF, "xlsx" to XLSX, "csv" to CSV, "docx" to DOCX, "hwpx" to HWPX)

        fun detect(mimeType: String?, fileName: String?): DocumentType {
            mimeType?.lowercase()?.substringBefore(';')?.trim()?.let { mime -> BY_MIME[mime]?.let { return it } }
            val extension = fileName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
            return BY_EXTENSION[extension] ?: UNSUPPORTED
        }
    }
}
