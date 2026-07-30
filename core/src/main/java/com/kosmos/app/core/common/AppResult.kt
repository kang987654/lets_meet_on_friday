package com.kosmos.app.core.common

/**
 * [AppResult]
 * 비즈니스 로직 및 데이터 작업의 성공([Success]) 또는 실패([Failure])를 명시적으로 표현하는 제네릭 Result 타입입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Common)
 * - **Dependencies**: [AppError]
 *
 * ### Key Flow
 * 1. 데이터 레이어 및 도메인 유즈케이스에서 작업 성공 시 데이터를 포장하거나 실패 시 [AppError]를 포장하여 반환합니다.
 * 2. [onSuccess], [onFailure], [getOrNull], [getOrElse] 확장을 통해 함수형 체이닝을 지원합니다.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> null
    }

inline fun <T> AppResult<T>.getOrElse(default: (AppError) -> T): T =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> default(error)
    }
