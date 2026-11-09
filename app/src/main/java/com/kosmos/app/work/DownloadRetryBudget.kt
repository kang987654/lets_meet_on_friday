package com.kosmos.app.work

import android.content.Context
import com.kosmos.app.core.common.Constants

/**
 * [DownloadRetryBudget]
 * 모델 다운로드의 "받은 양이 늘어난 시도" 수를 기억합니다 — 재시도 한도에서 그만큼 빼기 위해서입니다 (0.41.9).
 *
 * ### Key Flow
 * 1. 시도가 시작될 때 [onAttemptStart] — 부분 파일이 지난 시도 시작 때보다 커졌으면 지난 시도를 "진행 있음"으로 센다.
 *    Wi-Fi 끊김처럼 시스템이 끊은 시도는 워커가 결과를 돌려주지 못하므로, 끝이 아니라 **다음 시작**에서 센다.
 * 2. 포기 판정은 [shouldGiveUpDownload] — WorkManager 의 `runAttemptCount` 에서 진행 있던 시도를 뺀 값으로.
 * 3. 성공·확정 실패 때 [clear].
 *
 * [WHY] WorkManager 는 제약(Wi-Fi) 이탈로 중단될 때마다 `runAttemptCount` 를 올린다(2.12.0 `WorkerWrapper.trySetRunning`).
 * 그대로 한도에 쓰면 3.6GB 를 받다 네 번 끊긴 뒤 한 번의 일시 오류로 "다운로드 실패"가 됐다(0.41.9 외부 분석 W2 — 원인은 리뷰의 추정과 달랐다).
 * [WHY] 저장은 작은 SharedPreferences — 워커 입력 데이터는 시도 사이에 바꿀 수 없고, 프로세스가 죽어도 이어 세야 한다.
 */
internal class DownloadRetryBudget(context: Context, url: String, fileName: String?) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val key = "${fileName.orEmpty()}|$url"

    /**
     * 이번 시도 시작 — 지난 시도들 중 진행 있던 수를 돌려준다.
     *
     * @param runAttemptCount 0 이면 새 다운로드다 — 남은 기록을 지우고 시작한다.
     * @param startBytes 지금 부분 파일 크기
     */
    fun onAttemptStart(runAttemptCount: Int, startBytes: Long): Int {
        val previousStart = if (runAttemptCount == 0) null else prefs.getLong("$key$START", -1L).takeIf { it >= 0 }
        val previousCount = if (runAttemptCount == 0) 0 else prefs.getInt("$key$PROGRESSED", 0)
        val count = progressedAttemptsAfterStart(previousStart, startBytes, previousCount)
        prefs.edit().putLong("$key$START", startBytes).putInt("$key$PROGRESSED", count).apply()
        return count
    }

    fun clear() {
        prefs.edit().remove("$key$START").remove("$key$PROGRESSED").apply()
    }

    private companion object {
        const val PREFS = "model_download_budget"
        const val START = "#start"
        const val PROGRESSED = "#progressed"
    }
}

/** 지난 시도 시작 때보다 부분 파일이 커졌으면 지난 시도를 진행 있음으로 하나 더 센다. 처음이면(null) 그대로. */
internal fun progressedAttemptsAfterStart(previousStartBytes: Long?, startBytes: Long, previousCount: Int): Int =
    if (previousStartBytes != null && startBytes > previousStartBytes) previousCount + 1 else previousCount

/** 일시 실패 뒤 포기할지 — 진행 없던 시도만 한도([Constants.MODEL_DOWNLOAD_MAX_ATTEMPTS])를 쓴다. */
internal fun shouldGiveUpDownload(
    runAttemptCount: Int,
    progressedAttempts: Int,
    maxAttempts: Int = Constants.MODEL_DOWNLOAD_MAX_ATTEMPTS
): Boolean = runAttemptCount - progressedAttempts + 1 >= maxAttempts
