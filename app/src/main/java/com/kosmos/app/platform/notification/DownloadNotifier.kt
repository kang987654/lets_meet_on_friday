package com.kosmos.app.platform.notification

import android.Manifest
import android.app.Notification
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
 * [DownloadNotifier]
 * 모델 다운로드의 진행·완료·실패 알림을 만들고 게시합니다.
 *
 * ### Architecture Context
 * - **Layer**: App (Platform)
 * - **Dependencies**: [NotificationCompat], [NotificationChannels]
 *
 * [WHY] Worker 단위 테스트가 알림/PendingIntent 같은 Android 프레임워크에 얽히지 않도록
 * 인터페이스로 분리한다.
 */
interface DownloadNotifier {
    fun progressNotification(
        percent: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        cancelIntent: PendingIntent?
    ): Notification

    fun notifySuccess()

    fun notifyFailure(userMessage: String)
}

/**
 * [AndroidDownloadNotifier]
 * [DownloadNotifier]의 Android 구현입니다.
 *
 * ### Key Flow
 * 1. 진행 알림은 전경 서비스에 부착되므로 게시하지 않고 [Notification] 객체만 만들어 반환합니다.
 * 2. 종료 알림은 알림이 꺼져 있으면 조용히 건너뜁니다.
 */
@Singleton
class AndroidDownloadNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DownloadNotifier {

    override fun progressNotification(
        percent: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        cancelIntent: PendingIntent?
    ): Notification {
        val indeterminate = percent < 0
        val builder = NotificationCompat.Builder(context, NotificationChannels.MODEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setColor(accentColor())
            .setContentTitle("AI 모델 다운로드 중")
            .setContentText(progressText(downloadedBytes, totalBytes))
            .setProgress(100, percent.coerceAtLeast(0), indeterminate)
            .setOngoing(true)
            // [WHY] 진행률이 갱신될 때마다 알림음이 울리면 수십 분간 소음이 된다.
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(contentIntent())
        // [WHY] 액션 아이콘은 플랫폼 X 를 그대로 쓴다 — API 24+ 대부분의 알림 UI 는 액션
        // 아이콘을 표시하지 않고 라벨만 보여주므로 전용 벡터를 만들 이득이 없다.
        cancelIntent?.let { builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "취소", it) }
        return builder.build()
    }

    override fun notifySuccess() = notify(
        NotificationCompat.Builder(context, NotificationChannels.MODEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_download_done)
            .setColor(accentColor())
            .setContentTitle("모델 다운로드 완료")
            .setContentText("이제 대화를 시작할 수 있어요.")
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
    )

    override fun notifyFailure(userMessage: String) = notify(
        NotificationCompat.Builder(context, NotificationChannels.MODEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_error)
            .setColor(accentColor())
            .setContentTitle("모델 다운로드 실패")
            .setContentText(userMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(userMessage))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
    )

    /**
     * [WHY] 알림 권한이 거부되어 있으면 게시가 조용히 버려지거나 SecurityException 이 된다.
     * 다운로드 자체는 계속 유효하므로 게시 실패는 아무것도 깨뜨리지 않아야 한다 —
     * 권한과 채널 상태를 먼저 확인하고, 그래도 남는 예외는 삼킨다.
     */
    private fun notify(notification: Notification) {
        // API 33+ 에서 POST_NOTIFICATIONS 는 런타임 권한이다. 검사를 헬퍼로 빼면 lint 가
        // 흐름을 따라가지 못하므로 호출 지점에서 직접 확인한다.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        runCatching {
            manager.notify(NotificationChannels.NOTIF_ID_DOWNLOAD_RESULT, notification)
        }
    }

    /**
     * [WHY] 알림 아이콘 틴트 색은 프레임워크가 그리므로 Compose 토큰을 쓸 수 없다.
     * `res/values/colors.xml` 의 복제본을 참조한다(그 파일 주석 참조).
     */
    private fun accentColor(): Int =
        ContextCompat.getColor(context, R.color.notification_accent)

    private fun contentIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun progressText(downloadedBytes: Long, totalBytes: Long): String =
        if (totalBytes > 0) {
            "%.1fGB / %.1fGB".format(downloadedBytes.toGb(), totalBytes.toGb())
        } else {
            "%.1fGB 받았어요".format(downloadedBytes.toGb())
        }

    private fun Long.toGb(): Double = this / GB

    private companion object {
        const val GB = 1024.0 * 1024.0 * 1024.0
    }
}
