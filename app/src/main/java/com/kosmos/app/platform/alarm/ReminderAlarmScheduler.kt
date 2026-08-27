package com.kosmos.app.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.util.IsoDateTimeParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ReminderAlarmScheduler]
 * 리마인더의 AlarmManager 예약·취소·재부팅 복원을 담당합니다 (B1).
 *
 * ### Architecture Context
 * - **Layer**: App (Platform)
 * - **Dependencies**: Android [AlarmManager], [TaskRepository], [ReminderFireHandler]
 *
 * ### Key Flow
 * 1. [schedule] — 정확 알람 권한이 있으면 정각(setExactAndAllowWhileIdle), 없으면 ±10분
 *    창(setWindow)으로 자동 강등 (사용자 결정: 권한 없이도 동작은 유지)
 * 2. [cancel] — 같은 requestCode 의 PendingIntent 로 취소
 * 3. [restoreAll] — 미발화 리마인더를 전량 재등록. AlarmManager 는 재부팅에 살아남지 않는다.
 *    이미 지난 것은 즉시 1회 발화(놓친 알림을 무음 폐기하지 않는다)
 *
 * [WHY] 브리핑(WorkManager)과 달리 AlarmManager 인 이유: "3시에 알려줘"는 시점 정확도가
 * 정체성이다 — WorkManager 는 Doze 에서 수십 분 늦을 수 있다 (0.20.0 기각 근거의 재검토,
 * 사용자 결정 2026-08-28). 대가: 권한 2종 + 리시버 2개 + 재부팅 복원을 직접 진다.
 */
@Singleton
class ReminderAlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    private val fireHandler: ReminderFireHandler
) {
    fun schedule(taskId: String, triggerAtMs: Long) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val pi = alarmIntent(taskId)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canExact) {
            // [WHY] 권한이 방금 회수되는 경합에서 SecurityException 이 날 수 있다 — 창 모드로 강등.
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            }.onFailure {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMs, INEXACT_WINDOW_MS, pi)
            }
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMs, INEXACT_WINDOW_MS, pi)
        }
    }

    fun cancel(taskId: String) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        alarmManager.cancel(alarmIntent(taskId))
    }

    /** 재부팅 복원 — 미래는 재예약, 과거는 즉시 발화 1회. */
    suspend fun restoreAll(now: Long = System.currentTimeMillis()) {
        val reminders = when (val result = taskRepository.getActiveReminders()) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> {
                AppLogger.w(TAG, "리마인더 복원 조회 실패 — 이번 부팅은 건너뜀")
                return
            }
        }
        reminders.forEach { task ->
            val triggerAtMs = task.remindAtIso?.let { IsoDateTimeParser.toEpochMillis(it) }
                ?: return@forEach
            if (triggerAtMs > now) {
                schedule(task.id, triggerAtMs)
            } else {
                fireHandler.fire(task.id, now)
            }
        }
    }

    private fun alarmIntent(taskId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            // [WHY] requestCode 가 항목별로 달라야 리마인더 N건이 각자의 알람을 가진다 —
            // 고정 상수면 마지막 예약이 이전 것을 덮어쓴다 (기존 알림 전례와 갈라지는 지점).
            reminderStableId(taskId),
            Intent(context, ReminderAlarmReceiver::class.java)
                .putExtra(ReminderAlarmReceiver.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {
        const val TAG = "ReminderAlarmScheduler"

        // [WHY] 정확 알람 권한이 없을 때의 발화 허용 창. 시스템 최소 창(약 10분) 수준 —
        // 더 줄여도 시스템이 늘린다.
        const val INEXACT_WINDOW_MS = 10L * 60 * 1000
    }
}

/**
 * 리마인더의 알람 requestCode·알림 ID 를 taskId 에서 파생합니다.
 *
 * [WHY] 최상위 비트를 세워 0x40000000 이상으로 만든다 — 고정 상수(알림 1001~1003,
 * PendingIntent 0·3·4)와 절대 겹치지 않고, taskId(UUID)가 같으면 항상 같은 값이라
 * 예약·취소·재예약이 같은 슬롯을 가리킨다.
 */
internal fun reminderStableId(taskId: String): Int =
    0x40000000 or (taskId.hashCode() and 0x3FFFFFFF)
