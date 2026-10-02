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
    PDF, XLSX, CSV, UNSUPPORTED;

    companion object {
        /** 뷰어 "연결 프로그램" 필터·파일 선택기에 쓰는 MIME 목록 — 매니페스트와 같게 유지한다. */
        val VIEWABLE_MIME_TYPES: List<String> = listOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv",
            "text/comma-separated-values"
        )

        private val BY_MIME = mapOf(
            "application/pdf" to PDF,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to XLSX,
            "text/csv" to CSV,
            "text/comma-separated-values" to CSV,
            "application/csv" to CSV
        )

        private val BY_EXTENSION = mapOf("pdf" to PDF, "xlsx" to XLSX, "csv" to CSV)

        fun detect(mimeType: String?, fileName: String?): DocumentType {
            mimeType?.lowercase()?.substringBefore(';')?.trim()?.let { mime -> BY_MIME[mime]?.let { return it } }
            val extension = fileName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
            return BY_EXTENSION[extension] ?: UNSUPPORTED
        }
    }
}
