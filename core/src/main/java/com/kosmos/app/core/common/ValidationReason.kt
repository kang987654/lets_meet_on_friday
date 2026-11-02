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
 */
object ValidationReason {
    const val BLANK = "blank"
    const val TOO_LONG = "too_long"
}

/**
 * [ValidationField]
 * [AppError.ValidationError]의 `field`에 사용하는 표준 식별자입니다.
 */
object ValidationField {
    const val CONTENT = "content"
}
