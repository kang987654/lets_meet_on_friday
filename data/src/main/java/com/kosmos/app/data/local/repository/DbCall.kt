package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.logging.AppLogger

/**
 * 저장소 DB 호출의 오류 처리 단일 출처입니다 — 읽기 실패는 [AppError.DbReadError], 쓰기 실패는
 * [AppError.DbWriteError], 취소는 되던진다.
 *
 * [WHY] 같은 일을 세 가지 모양(runCatchingCancellable.fold / 손으로 쓴 try-catch / 저장소마다
 * 사설 read·write 헬퍼)으로 하고 있었고, 그 차이가 실제 결함을 숨겼다 — 일부는 실패 로그가
 * 없었고, 지식 저장소는 DB 오류를 SearchError 로 분류해 사용자에게 "검색이 지연되고 있어요"를
 * 띄웠다. 반환 오류 종류는 테이블 이름만 인자로 받아 저장소마다 달라질 여지를 없앤다.
 *
 * [WHY] inline — 블록 안에서 DAO 의 suspend 함수를 부를 수 있어야 한다(호출부가 suspend).
 */
internal inline fun <T> dbRead(table: String, what: String, block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: kotlin.coroutines.cancellation.CancellationException) {
    throw e
} catch (e: Exception) {
    AppLogger.e(DB_CALL_TAG, "[$table] $what 실패", e)
    AppResult.Failure(AppError.DbReadError(table))
}

/** 쓰기 버전 — 결과 값이 없는 DAO 호출용. */
internal inline fun dbWrite(table: String, what: String, block: () -> Unit): AppResult<Unit> = try {
    block()
    AppResult.Success(Unit)
} catch (e: kotlin.coroutines.cancellation.CancellationException) {
    throw e
} catch (e: Exception) {
    AppLogger.e(DB_CALL_TAG, "[$table] $what 실패", e)
    AppResult.Failure(AppError.DbWriteError(table))
}


internal const val DB_CALL_TAG = "DbCall"
