package com.kosmos.app.platform.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import androidx.core.content.getSystemService
import com.kosmos.app.R
import com.kosmos.app.core.common.AppLanguage
import java.util.Locale

/**
 * [NotificationChannels]
 * 앱이 사용하는 알림 채널을 정의하고 생성합니다.
 *
 * ### Architecture Context
 * - **Layer**: App (Platform)
 * - **Dependencies**: Android [NotificationManager]
 *
 * ### Key Flow
 * 1. [ensureCreated]가 앱 시작 시(`KosmosApp.onCreate`) 및 언어 변경 시 채널을 등록합니다.
 * 2. 이미 존재하는 채널을 다시 만들어도 무해하며, 이름과 설명만 새 언어로 갱신됩니다.
 *
 * [WHY] minSdk 26이므로 채널은 항상 필요하다 — SDK 버전 분기가 없다.
 */
object NotificationChannels {

    const val MODEL_DOWNLOAD = "model_download"
    const val BRIEFING = "morning_briefing"
    const val REMINDER = "reminder"

    const val NOTIF_ID_DOWNLOAD_PROGRESS = 1001
    const val NOTIF_ID_DOWNLOAD_RESULT = 1002
    const val NOTIF_ID_BRIEFING = 1003
    // 리마인더 알림 ID 는 고정 상수가 아니라 항목별 파생 — reminderStableId (B1)

    fun ensureCreated(context: Context, lang: AppLanguage = AppLanguage.KO) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val targetLocale = if (lang == AppLanguage.EN) Locale.ENGLISH else Locale.KOREAN
        val resContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(targetLocale) }
        )
        // [WHY] 진행률 바가 소리나 진동을 내면 수십 분간 사용자를 괴롭히므로 IMPORTANCE_LOW + 무음.
        val channel = NotificationChannel(
            MODEL_DOWNLOAD,
            resContext.getString(R.string.notif_channel_download_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = resContext.getString(R.string.notif_channel_download_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)

        // [WHY] 다운로드와 달리 사용자를 부르는 알림이므로 기본 중요도(소리·배지 시스템 기본).
        // 채널이 분리돼 있어야 사용자가 시스템 설정에서 브리핑만 무음/차단할 수 있다 (A4).
        val briefing = NotificationChannel(
            BRIEFING,
            resContext.getString(R.string.notif_channel_briefing_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = resContext.getString(R.string.notif_channel_briefing_desc)
        }
        manager.createNotificationChannel(briefing)

        // [WHY] 시점 알림은 그 시각을 놓치면 존재 이유가 없다 — 헤드업(IMPORTANCE_HIGH).
        // 과하다고 느끼면 사용자가 채널 설정에서 낮출 수 있다 (B1).
        val reminder = NotificationChannel(
            REMINDER,
            resContext.getString(R.string.notif_channel_reminder_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = resContext.getString(R.string.notif_channel_reminder_desc)
        }
        manager.createNotificationChannel(reminder)
    }
}
