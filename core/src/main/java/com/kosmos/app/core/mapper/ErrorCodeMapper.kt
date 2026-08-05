package com.kosmos.app.core.mapper

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.ValidationReason
import com.kosmos.app.core.security.PermissionPolicy

/**
 * [ErrorCodeMapper]
 * 내부 도메인/시스템 에러([AppError])를 UI 표출용 정규화 오류 코드([ErrorCode])로 변환하는 매퍼 싱글톤입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Mapper)
 * - **Dependencies**: [AppError], [ErrorCode], [PermissionPolicy]
 *
 * ### Key Flow
 * 1. [AppError] 인스턴스를 전달받아 패턴 매칭 및 필드 분석을 통해 UI 표출용 [ErrorCode]를 1:1로 변환합니다.
 */
object ErrorCodeMapper {
    fun toErrorCode(error: AppError): ErrorCode = when (error) {
        // [WHY] 부분 일치 추측을 제거하고 표준 토큰([ValidationReason])과 정확히 매칭한다.
        // 미분류는 INPUT_TOO_LONG이 아니라 INVALID_INPUT으로 떨어뜨려 오분류를 없앤다.
        is AppError.ValidationError -> when (error.reason) {
            ValidationReason.BLANK -> ErrorCode.EMPTY_INPUT
            ValidationReason.TOO_LONG -> ErrorCode.INPUT_TOO_LONG
            ValidationReason.MISSING_TIME -> ErrorCode.MISSING_TIME_INFO
            else -> ErrorCode.INVALID_INPUT
        }

        is AppError.ImageTooLarge -> ErrorCode.IMAGE_TOO_LARGE
        is AppError.UnsupportedImageFormat -> ErrorCode.UNSUPPORTED_IMAGE_FORMAT
        is AppError.InFlightConflict -> ErrorCode.IN_FLIGHT_CONFLICT
        is AppError.DuplicateEvent -> ErrorCode.DUPLICATE_EVENT

        is AppError.PermissionDenied -> when (error.permission) {
            PermissionPolicy.CALENDAR_READ,
            PermissionPolicy.CALENDAR_WRITE -> ErrorCode.PERMISSION_DENIED_CALENDAR

            PermissionPolicy.MICROPHONE -> ErrorCode.PERMISSION_DENIED_MICROPHONE

            else -> ErrorCode.PERMISSION_DENIED_STORAGE
        }

        is AppError.ModelNotFound -> ErrorCode.MODEL_NOT_FOUND
        is AppError.ModelNotReady -> ErrorCode.MODEL_NOT_READY
        is AppError.ModelInferenceTimeout -> ErrorCode.MODEL_INFERENCE_TIMEOUT
        is AppError.ModelInferenceError -> ErrorCode.MODEL_INFERENCE_ERROR

        is AppError.CalendarReadError,
        is AppError.CalendarWriteError -> ErrorCode.CALENDAR_PROVIDER_ERROR

        is AppError.SttError -> ErrorCode.STT_ERROR

        is AppError.NetworkUnavailable -> ErrorCode.NETWORK_UNAVAILABLE
        is AppError.SearchError -> ErrorCode.SEARCH_TIMEOUT

        is AppError.DbWriteError -> ErrorCode.DB_WRITE_ERROR
        is AppError.DbReadError -> ErrorCode.DB_READ_ERROR
        is AppError.DbMigrationError -> ErrorCode.DB_MIGRATION_ERROR

        is AppError.ExportFailed -> ErrorCode.EXPORT_FAILED
        is AppError.ImportFailed -> ErrorCode.IMPORT_FAILED
        is AppError.ImportManifestMismatch -> ErrorCode.IMPORT_MANIFEST_MISMATCH
        is AppError.ImportSchemaMismatch -> ErrorCode.IMPORT_SCHEMA_MISMATCH
        is AppError.InsufficientStorage -> ErrorCode.INSUFFICIENT_STORAGE
        is AppError.TemperatureWarning -> ErrorCode.TEMPERATURE_WARNING
        is AppError.TemperatureCritical -> ErrorCode.TEMPERATURE_CRITICAL
    }
}
