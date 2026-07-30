package com.kosmos.app.core.common

/**
 * [AppError]
 * 애플리케이션 전역에서 발생하는 도메인, 데이터, 모델 및 플랫폼 에러를 세분화하여 정의한 봉인 클래스(sealed class)입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Common)
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. [AppResult.Failure]에 담겨 계층 간 에러 전달 시 사용됩니다.
 */
sealed class AppError {
    data class ModelNotFound(val path: String) : AppError()
    data class ModelNotReady(val reason: String) : AppError()
    data class ModelInferenceTimeout(val durationMs: Long) : AppError()
    data class ModelInferenceError(val reason: String) : AppError()

    data class DbWriteError(val table: String) : AppError()
    data class DbReadError(val table: String) : AppError()
    data class DbMigrationError(val from: Int, val to: Int) : AppError()

    data class PermissionDenied(val permission: String) : AppError()

    data class CalendarReadError(val reason: String) : AppError()
    data class CalendarWriteError(val reason: String) : AppError()
    data class SttError(val reason: String) : AppError()

    data class ValidationError(val field: String, val reason: String) : AppError()
    data class ImageTooLarge(val sizeBytes: Long) : AppError()
    data class UnsupportedImageFormat(val mimeType: String) : AppError()

    data class SearchError(val reason: String) : AppError()
    data class NetworkUnavailable(val reason: String) : AppError()

    data class ExportFailed(val reason: String) : AppError()
    data class ImportFailed(val reason: String) : AppError()
    data class ImportManifestMismatch(val expected: String, val actual: String) : AppError()
    data class ImportSchemaMismatch(val reason: String) : AppError()
    data class InsufficientStorage(val requiredBytes: Long) : AppError()

    data class InFlightConflict(val reason: String) : AppError()
    data class DuplicateEvent(val reason: String) : AppError()

    data class TemperatureWarning(val temperature: Float) : AppError()
    data class TemperatureCritical(val temperature: Float) : AppError()
}
