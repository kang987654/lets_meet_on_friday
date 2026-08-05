package com.kosmos.app.core.mapper

/**
 * [ErrorCode]
 * 사용자 표출용 에러 코드 식별자를 정의하는 열거형(enum)입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Mapper)
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. 도메인/플랫폼의 [AppError]를 UI 레이어 메시지로 매핑할 때 정규화된 오류 코드로 참조됩니다.
 */
enum class ErrorCode {
    EMPTY_INPUT,
    INPUT_TOO_LONG,
    MISSING_TIME_INFO,
    /** 위 세 가지로 분류되지 않는 입력 검증 실패 (기존에는 INPUT_TOO_LONG으로 오분류됐음) */
    INVALID_INPUT,
    IMAGE_TOO_LARGE,
    UNSUPPORTED_IMAGE_FORMAT,
    IN_FLIGHT_CONFLICT,
    DUPLICATE_EVENT,
    PERMISSION_DENIED_CALENDAR,
    PERMISSION_DENIED_MICROPHONE,
    PERMISSION_DENIED_STORAGE,
    MODEL_NOT_FOUND,
    MODEL_NOT_READY,
    MODEL_INFERENCE_TIMEOUT,
    MODEL_INFERENCE_ERROR,
    CALENDAR_PROVIDER_ERROR,
    STT_ERROR,
    NETWORK_UNAVAILABLE,
    SEARCH_TIMEOUT,
    DB_WRITE_ERROR,
    DB_READ_ERROR,
    DB_MIGRATION_ERROR,
    EXPORT_FAILED,
    IMPORT_FAILED,
    IMPORT_MANIFEST_MISMATCH,
    IMPORT_SCHEMA_MISMATCH,
    INSUFFICIENT_STORAGE,
    TEMPERATURE_WARNING,
    TEMPERATURE_CRITICAL
}
