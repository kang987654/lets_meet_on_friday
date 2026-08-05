package com.kosmos.app.core.common

/**
 * [ValidationReason]
 * [AppError.ValidationError]의 `reason`에 사용하는 표준 토큰입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Common)
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. 검증 실패를 만드는 쪽(UseCase/Platform)이 이 상수를 `reason`으로 전달합니다.
 * 2. `ErrorCodeMapper`가 문자열 부분 일치 추측 없이 이 토큰과 정확히 매칭해 ErrorCode를 정합니다.
 *
 * [WHY] 과거에는 사람이 읽는 한국어 문장을 reason에 넣고 매퍼가 substring으로 추측해
 * "timeout"이 MISSING_TIME_INFO로 분류되는 등 오분류가 발생했다. (docs/api_spec.yaml 매핑 계약과도 불일치)
 */
object ValidationReason {
    const val BLANK = "blank"
    const val TOO_LONG = "too_long"
    const val MISSING_TIME = "missing_time"
}

/**
 * [ValidationField]
 * [AppError.ValidationError]의 `field`에 사용하는 표준 식별자입니다.
 * `docs/api_spec.yaml`의 AppError ↔ ErrorCode 매핑 계약을 따릅니다.
 */
object ValidationField {
    const val CONTENT = "content"
    const val NATURAL_LANGUAGE_REQUEST = "naturalLanguageRequest"
}
