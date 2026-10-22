package com.kosmos.app.platform.notification

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 알림이 실제로 화면에 뜰 수 있는지 — 알림 권한(API 33+)과 정확 알람 권한(API 31+) 확인.
 *
 * [WHY] 알림 권한을 모델 내려받기 화면에서만 요청해, 모델 파일을 직접 넣은 기기는 한 번도 요청받지 못했다 — 리마인더는 울리는데
 * 화면에 아무것도 뜨지 않았다(0.36.0 실기기). 알림을 만드는 기능(리마인더 승인·브리핑 켜기)이 이 확인을 각자의 시점에 쓴다.
 */
object NotificationAccess {

    /** 요청이 필요한 런타임 권한 — API 33 미만은 권한이 없어 null. */
    val runtimePermission: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null

    fun canPost(context: Context): Boolean {
        val permission = runtimePermission ?: return true
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** 정확 알람을 걸 수 있는가 — 없으면 [com.kosmos.app.platform.alarm.ReminderAlarmScheduler] 가 창(최대 10분) 알람으로 건다. */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() != false

    const val DENIED_MESSAGE = "알림 권한이 꺼져 있어 알림이 화면에 뜨지 않아요. 설정 > 애플리케이션 > Kosmos > 알림에서 켤 수 있어요."
    /** 정확한 알람 허용 화면 — 승인 시트의 "설정에서 켜기"(0.38.2 M8). API 31 미만은 늘 정확해서 부르지 않는다. */
    fun exactAlarmSettingsIntent(context: Context): android.content.Intent =
        android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))

    const val INEXACT_NOTE = "알람 및 리마인더 권한이 없어 알림이 최대 10분 늦을 수 있어요."
}
