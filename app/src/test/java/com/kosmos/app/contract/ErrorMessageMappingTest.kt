package com.kosmos.app.contract

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.ValidationField
import com.kosmos.app.core.common.ValidationReason
import com.kosmos.app.core.mapper.ErrorCode
import com.kosmos.app.core.mapper.ErrorCodeMapper
import com.kosmos.app.core.mapper.ErrorMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ErrorMessageMappingTest]
 * ValidationError 표준 토큰 매핑과 사용자 문구 노출 규칙을 검증합니다.
 * (2026-07-31 감사 후속 B-1: substring 휴리스틱 오분류 및 raw 클래스명 노출 회귀 방지)
 */
class ErrorMessageMappingTest {

    @Test
    fun `canonical validation reasons map to their error codes`() {
        assertEquals(
            ErrorCode.EMPTY_INPUT,
            ErrorCodeMapper.toErrorCode(AppError.ValidationError(ValidationField.CONTENT, ValidationReason.BLANK))
        )
        assertEquals(
            ErrorCode.INPUT_TOO_LONG,
            ErrorCodeMapper.toErrorCode(AppError.ValidationError(ValidationField.CONTENT, ValidationReason.TOO_LONG))
        )
        assertEquals(
            ErrorCode.MISSING_TIME_INFO,
            ErrorCodeMapper.toErrorCode(
                AppError.ValidationError(ValidationField.NATURAL_LANGUAGE_REQUEST, ValidationReason.MISSING_TIME)
            )
        )
    }

    @Test
    fun `unknown validation reason falls back to INVALID_INPUT instead of INPUT_TOO_LONG`() {
        val code = ErrorCodeMapper.toErrorCode(AppError.ValidationError("someField", "someReason"))
        assertEquals(ErrorCode.INVALID_INPUT, code)
    }

    @Test
    fun `reason containing the word timeout is not misclassified as missing time`() {
        // [WHY] 구 매퍼는 reason에 "time"이 들어가면 MISSING_TIME_INFO로 분류했다.
        val code = ErrorCodeMapper.toErrorCode(AppError.ValidationError(ValidationField.CONTENT, "timeout"))
        assertFalse(code == ErrorCode.MISSING_TIME_INFO)
    }

    @Test
    fun `user messages never leak internal class names`() {
        val errors = listOf(
            AppError.DbWriteError("task_item"),
            AppError.DbReadError("conversation"),
            AppError.ModelNotReady("engine warming"),
            AppError.NetworkUnavailable("HTTP 503"),
            AppError.ImageTooLarge(20_000_000L),
            AppError.InsufficientStorage(0L),
            AppError.PermissionDenied("android.permission.RECORD_AUDIO"),
            AppError.ImportFailed("bad zip")
        )

        errors.forEach { error ->
            val message = ErrorMessages.userMessage(error)
            assertTrue("빈 문구: $error", message.isNotBlank())
            assertFalse("클래스명 노출: $message", message.contains("AppError"))
            assertFalse("내부 식별자 노출: $message", message.contains("task_item"))
            assertFalse("권한 상수 노출: $message", message.contains("android.permission"))
        }
    }

    @Test
    fun `every error code has a user message`() {
        // 코드가 추가되면 when 분기 누락을 컴파일 타임에 잡지만, 빈 문자열 방지도 함께 확인한다.
        ErrorCode.entries.forEach { code ->
            val error = when (code) {
                ErrorCode.EMPTY_INPUT ->
                    AppError.ValidationError(ValidationField.CONTENT, ValidationReason.BLANK)
                ErrorCode.INPUT_TOO_LONG ->
                    AppError.ValidationError(ValidationField.CONTENT, ValidationReason.TOO_LONG)
                ErrorCode.MISSING_TIME_INFO ->
                    AppError.ValidationError(ValidationField.NATURAL_LANGUAGE_REQUEST, ValidationReason.MISSING_TIME)
                ErrorCode.INVALID_INPUT -> AppError.ValidationError("x", "y")
                ErrorCode.IMAGE_TOO_LARGE -> AppError.ImageTooLarge(1L)
                ErrorCode.UNSUPPORTED_IMAGE_FORMAT -> AppError.UnsupportedImageFormat("x")
                ErrorCode.IN_FLIGHT_CONFLICT -> AppError.InFlightConflict("x")
                ErrorCode.DUPLICATE_EVENT -> AppError.DuplicateEvent("x")
                ErrorCode.PERMISSION_DENIED_CALENDAR -> AppError.PermissionDenied("android.permission.READ_CALENDAR")
                ErrorCode.PERMISSION_DENIED_MICROPHONE -> AppError.PermissionDenied("android.permission.RECORD_AUDIO")
                ErrorCode.PERMISSION_DENIED_STORAGE -> AppError.PermissionDenied("other")
                ErrorCode.MODEL_NOT_FOUND -> AppError.ModelNotFound("p")
                ErrorCode.MODEL_NOT_READY -> AppError.ModelNotReady("r")
                ErrorCode.MODEL_INFERENCE_TIMEOUT -> AppError.ModelInferenceTimeout(1L)
                ErrorCode.MODEL_INFERENCE_ERROR -> AppError.ModelInferenceError("r")
                ErrorCode.CALENDAR_PROVIDER_ERROR -> AppError.CalendarReadError("r")
                ErrorCode.STT_ERROR -> AppError.SttError("r")
                ErrorCode.NETWORK_UNAVAILABLE -> AppError.NetworkUnavailable("r")
                ErrorCode.SEARCH_TIMEOUT -> AppError.SearchError("r")
                ErrorCode.DB_WRITE_ERROR -> AppError.DbWriteError("t")
                ErrorCode.DB_READ_ERROR -> AppError.DbReadError("t")
                ErrorCode.DB_MIGRATION_ERROR -> AppError.DbMigrationError(1, 2)
                ErrorCode.EXPORT_FAILED -> AppError.ExportFailed("r")
                ErrorCode.IMPORT_FAILED -> AppError.ImportFailed("r")
                ErrorCode.IMPORT_MANIFEST_MISMATCH -> AppError.ImportManifestMismatch("1.0", "2.0")
                ErrorCode.IMPORT_SCHEMA_MISMATCH -> AppError.ImportSchemaMismatch("r")
                ErrorCode.INSUFFICIENT_STORAGE -> AppError.InsufficientStorage(1L)
                ErrorCode.TEMPERATURE_WARNING -> AppError.TemperatureWarning(44f)
                ErrorCode.TEMPERATURE_CRITICAL -> AppError.TemperatureCritical(49f)
            }
            assertEquals(code, ErrorCodeMapper.toErrorCode(error))
            assertTrue("문구 없음: $code", ErrorMessages.userMessage(error).isNotBlank())
        }
    }
}
