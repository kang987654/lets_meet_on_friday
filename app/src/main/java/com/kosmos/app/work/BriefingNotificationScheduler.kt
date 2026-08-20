package com.kosmos.app.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kosmos.app.core.common.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BriefingNotificationScheduler]
 * 아침 브리핑 미리보기 알림의 정시 예약을 관리합니다 (A4).
 *
 * ### Architecture Context
 * - **Layer**: App (Work)
 * - **Dependencies**: [WorkManager]
 *
 * [WHY] AlarmManager(정확 알람)가 아니라 WorkManager 다 — 정확 알람은 SCHEDULE_EXACT_ALARM
 * 권한이 필요하고, 미리보기 알림 하나에 과한 비용이다. Doze 에서 수 분 밀릴 수 있지만
 * WorkManager 는 **재부팅에도 예약이 살아남아** BOOT_COMPLETED 리시버가 필요 없다.
 */
@Singleton
class BriefingNotificationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** 앱 시작 시 멱등 보장 — 이미 예약돼 있으면 그대로 둔다 (KEEP). */
    fun ensureScheduled(timeMinutes: Int) = enqueue(timeMinutes, ExistingWorkPolicy.KEEP)

    /** 설정 변경 시 — 기존 예약을 새 시각으로 교체한다. */
    fun reschedule(timeMinutes: Int) = enqueue(timeMinutes, ExistingWorkPolicy.REPLACE)

    /**
     * 워커가 다음날 자기 자신을 재예약할 때 — REPLACE 를 쓰면 **실행 중인 자기 자신을
     * 취소**할 수 있다. APPEND_OR_REPLACE 는 실행 중이면 뒤에 잇고, 끝난 체인이면 교체한다.
     */
    fun rescheduleFromWorker(timeMinutes: Int) =
        enqueue(timeMinutes, ExistingWorkPolicy.APPEND_OR_REPLACE)

    /** 토글 off — 예약 취소. */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(Constants.BRIEFING_WORK_NAME)
    }

    private fun enqueue(timeMinutes: Int, policy: ExistingWorkPolicy) {
        val delay = nextTriggerDelayMillis(System.currentTimeMillis(), timeMinutes, ZoneId.systemDefault())
        val request = OneTimeWorkRequestBuilder<BriefingNotificationWorker>()
            // [WHY] 제약 없음 — 로컬 DB 읽기와 알림 게시뿐이라 네트워크·충전 조건이 필요 없다.
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(Constants.BRIEFING_WORK_NAME, policy, request)
    }
}

/**
 * 다음 알림 시각(오늘 또는 내일의 [timeMinutes])까지의 지연을 계산합니다 — 테스트 가능하도록
 * 순수 함수로 분리 (toDownloadStatus 전례).
 */
internal fun nextTriggerDelayMillis(nowMs: Long, timeMinutes: Int, zone: ZoneId): Long {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val todayTrigger = today.atStartOfDay(zone).toInstant().toEpochMilli() + timeMinutes * 60_000L
    return if (nowMs < todayTrigger) {
        todayTrigger - nowMs
    } else {
        // [WHY] LocalDate 로 하루를 더한다 — 고정 24시간 덧셈은 DST 전환일에 시각이 밀린다.
        val tomorrowTrigger = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() +
            timeMinutes * 60_000L
        tomorrowTrigger - nowMs
    }
}
