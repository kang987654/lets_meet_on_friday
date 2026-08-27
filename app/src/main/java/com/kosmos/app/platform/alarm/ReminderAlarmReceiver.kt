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
 * [ReminderAlarmReceiver]
 * AlarmManager 발화를 받아 [ReminderFireHandler] 로 위임하는 얇은 리시버입니다 (B1).
 *
 * ### Architecture Context
 * - **Layer**: App (Platform)
 * - **Dependencies**: [ReminderFireHandler]
 *
 * [WHY] goAsync — onReceive 는 메인 스레드에서 반환 즉시 프로세스가 죽을 수 있다.
 * goAsync 가 ~10초 창을 열어 주므로 DB 재확인 + 알림 게시가 안전하게 끝난다.
 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var fireHandler: ReminderFireHandler

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fireHandler.fire(taskId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}
