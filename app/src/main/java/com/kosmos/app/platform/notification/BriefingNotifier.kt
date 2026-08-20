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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BriefingNotifier]
 * 아침 브리핑의 정시 미리보기 알림을 게시합니다 (A4 하이브리드 — 알림은 추론 없이 DB 숫자만).
 *
 * ### Architecture Context
 * - **Layer**: App (Platform)
 * - **Dependencies**: Android Notification
 *
 * [WHY] 인터페이스로 분리한다 — Worker 단위 테스트를 프레임워크에서 떼기 위함
 * (DownloadNotifier 전례).
 */
interface BriefingNotifier {
    fun notifyPreview(eventCount: Int, taskCount: Int, deviceCalendarFailed: Boolean)
}

@Singleton
class AndroidBriefingNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) : BriefingNotifier {

    override fun notifyPreview(eventCount: Int, taskCount: Int, deviceCalendarFailed: Boolean) {
        val body = buildString {
            append("오늘 일정 ${eventCount}건 · 남은 할 일 ${taskCount}건")
            if (deviceCalendarFailed) append("\n기기 캘린더는 확인하지 못했어요.")
        }
        val notification = NotificationCompat.Builder(context, NotificationChannels.BRIEFING)
            .setSmallIcon(R.drawable.ic_stat_briefing)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle("좋은 아침이에요 ☀️")
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body + "\n앱을 열면 브리핑이 도착해요."))
            .setAutoCancel(true)
            // [WHY] 딥링크가 필요 없다 — 브리핑 카드는 타임라인 최신 위치라 앱만 열리면 보인다.
            .setContentIntent(contentIntent())
            .build()

        // [WHY] 권한 검사를 헬퍼로 빼면 lint 가 흐름을 못 따라간다 — 호출 지점에서 직접
        // (DownloadNotifier 와 동일). 미허용이면 조용히 생략 — 알림만 없을 뿐 카드 생성은
        // 생성기가 정상 수행한다.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        runCatching {
            manager.notify(NotificationChannels.NOTIF_ID_BRIEFING, notification)
        }
    }

    private fun contentIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {
        // [WHY] 다운로드 알림(requestCode 0)과 다른 코드 — 같으면 PendingIntent 가 공유된다.
        const val REQUEST_CODE = 3
    }
}
