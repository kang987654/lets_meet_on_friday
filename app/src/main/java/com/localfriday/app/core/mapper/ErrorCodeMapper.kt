package com.localfriday.app.core.mapper

import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.security.PermissionPolicy

object ErrorCodeMapper {
    fun toErrorCode(error: AppError): ErrorCode = when (error) {
        is AppError.ValidationError -> when {
            error.field == "content" && error.reason.contains("blank", ignoreCase = true) ->
                ErrorCode.EMPTY_INPUT

            error.field == "content" && (
                error.reason.contains("too_long", ignoreCase = true) ||
                error.reason.contains("length", ignoreCase = true) ||
                error.reason.contains("길", ignoreCase = true)
            ) -> ErrorCode.INPUT_TOO_LONG

            error.field == "naturalLanguageRequest" && (
                error.reason.contains("missing_time", ignoreCase = true) ||
                error.reason.contains("time", ignoreCase = true) ||
                error.reason.contains("시간", ignoreCase = true)
            ) -> ErrorCode.MISSING_TIME_INFO

            else -> ErrorCode.INPUT_TOO_LONG
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
        is AppError.DbMigrationError -> ErrorCode.DB_MIGRATION_ERROR

        is AppError.ExportFailed -> ErrorCode.EXPORT_FAILED
        is AppError.ImportFailed -> ErrorCode.IMPORT_FAILED
        is AppError.ImportManifestMismatch -> ErrorCode.IMPORT_MANIFEST_MISMATCH
        is AppError.ImportSchemaMismatch -> ErrorCode.IMPORT_SCHEMA_MISMATCH
        is AppError.InsufficientStorage -> ErrorCode.INSUFFICIENT_STORAGE
    }
}
