package com.kosmos.app.domain.tool

/**
 * [ModelDownloadException]
 * 모델 다운로드 실패를 재시도 가능 여부에 따라 구분하는 예외 계층입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (Tool)
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. `:data`의 다운로더가 HTTP 응답 코드와 I/O 예외를 이 타입으로 번역합니다.
 * 2. Worker가 [Transient]에만 `Result.retry()`를 반환합니다.
 *
 * [WHY] 이전 구현은 예외 메시지에 "space"/"ENOSPC" 문자열이 있는지로 저장공간 부족을
 * 판정했다. 문구가 바뀌면 조용히 오분류되므로 `ValidationReason` 도입 때와 같은 이유로
 * 타입으로 승격한다.
 */
sealed class ModelDownloadException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** 재시도 가능 — 네트워크 끊김, 타임아웃, HTTP 5xx/408/429. */
    class Transient(message: String, cause: Throwable? = null) :
        ModelDownloadException(message, cause)

    /** 재시도 무의미 — HTTP 4xx(408/429 제외), 잘못된 URL, 크기 검증 실패, 파일 확정 실패. */
    class Permanent(message: String, cause: Throwable? = null) :
        ModelDownloadException(message, cause)

    /** 저장 공간 부족 — 실제 필요/가용 바이트 수를 함께 전달해 UI가 안내할 수 있게 한다. */
    class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) :
        ModelDownloadException(
            "Insufficient storage: need $requiredBytes bytes, have $availableBytes bytes"
        )
}
