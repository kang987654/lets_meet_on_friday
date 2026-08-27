package com.kosmos.app.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [ReminderBootReceiver]
 * 재부팅 후 리마인더 알람을 복원합니다 (B1).
 *
 * ### Architecture Context
 * - **Layer**: App (Platform)
 * - **Dependencies**: [ReminderAlarmScheduler]
 *
 * [WHY] AlarmManager 예약은 재부팅에 살아남지 않는다 — WorkManager(브리핑)와 달리
 * BOOT_COMPLETED 복원을 직접 진다. 이것이 정확 알람을 택한 대가의 일부다.
 */
@AndroidEntryPoint
class ReminderBootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduler.restoreAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
