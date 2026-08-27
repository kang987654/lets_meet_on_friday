package com.kosmos.app.platform.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kosmos.app.MainActivity
import com.kosmos.app.R
import com.kosmos.app.platform.alarm.reminderStableId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ReminderNotifier]
 * 지정 시각에 도달한 리마인더를 알림으로 게시합니다 (B1).
 *
 * ### Architecture Context
 * - **Layer**: App (Platform)
 * - **Dependencies**: Android Notification
 *
 * [WHY] 인터페이스로 분리한다 — 발화 핸들러 단위 테스트를 프레임워크에서 떼기 위함
 * (BriefingNotifier 전례).
 */
interface ReminderNotifier {
    fun notifyReminder(taskId: String, content: String, timeDisplay: String?)
}

@Singleton
class AndroidReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ReminderNotifier {

    override fun notifyReminder(taskId: String, content: String, timeDisplay: String?) {
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDER)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle("⏰ $content")
            .apply { timeDisplay?.let { setContentText(it) } }
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()

        // [WHY] 권한 검사를 헬퍼로 빼면 lint 가 흐름을 못 따라간다 — 호출 지점에서 직접
        // (BriefingNotifier 와 동일). 미허용이면 조용히 생략 — 리마인더 저장·발화 기록은 유지된다.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        runCatching {
            // [WHY] 알림 ID 를 항목별로 파생한다 — 고정 상수면 리마인더 두 개가 서로를 덮어쓴다.
            manager.notify(reminderStableId(taskId), notification)
        }
    }

    private fun contentIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            // [WHY] SINGLE_TOP — MainActivity 가 singleTop(0.22.0)이라 앱이 떠 있으면
            // 재생성 없이 onNewIntent 로 전달된다.
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {
        // [WHY] 다운로드(0)·브리핑(3)과 다른 코드 — 같으면 PendingIntent 가 공유된다.
        const val REQUEST_CODE = 4
    }
}
