package com.kosmos.app.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatchingCancellable]
 * 코루틴 취소를 존중하는 `runCatching` 대체 함수입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Common)
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. 블록 실행 중 [CancellationException]은 그대로 rethrow하여 구조적 취소를 보존합니다.
 *    ([WHY] 취소를 `AppResult.Failure`로 변환하면 취소된 ViewModel 스코프가 가짜 "DB 오류"로
 *    표면화되고 취소 전파가 끊긴다.)
 * 2. 그 외 예외는 [Result.failure]로 감싸 호출부의 `fold` 매핑에 위임합니다.
 */
// [WHY] inline으로 두면 :core(JVM 21)와 소비 모듈(:data, JVM 17)의 타깃 불일치로
// 컴파일이 깨지므로 일반 함수로 유지한다. suspend 블록 지원을 위해 suspend 시그니처 오버로드 제공.
suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
