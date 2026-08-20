package com.kosmos.app.platform.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * [NotificationChannels]
 * 앱이 사용하는 알림 채널을 정의하고 생성합니다.
 *
 * ### Architecture Context
 * - **Layer**: App (Platform)
 * - **Dependencies**: Android [NotificationManager]
 *
 * ### Key Flow
 * 1. [ensureCreated]가 앱 시작 시(`KosmosApp.onCreate`) 채널을 등록합니다.
 * 2. 이미 존재하는 채널을 다시 만들어도 무해하므로 존재 여부를 검사하지 않습니다.
 *
 * [WHY] minSdk 26이므로 채널은 항상 필요하다 — SDK 버전 분기가 없다.
 */
object NotificationChannels {

    const val MODEL_DOWNLOAD = "model_download"
    const val BRIEFING = "morning_briefing"

    const val NOTIF_ID_DOWNLOAD_PROGRESS = 1001
    const val NOTIF_ID_DOWNLOAD_RESULT = 1002
    const val NOTIF_ID_BRIEFING = 1003

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        // [WHY] 진행률 바가 소리나 진동을 내면 수십 분간 사용자를 괴롭히므로 IMPORTANCE_LOW + 무음.
        val channel = NotificationChannel(
            MODEL_DOWNLOAD,
            "모델 다운로드",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "AI 모델 파일 다운로드 진행 상황을 표시합니다."
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)

        // [WHY] 다운로드와 달리 사용자를 부르는 알림이므로 기본 중요도(소리·배지 시스템 기본).
        // 채널이 분리돼 있어야 사용자가 시스템 설정에서 브리핑만 무음/차단할 수 있다 (A4).
        val briefing = NotificationChannel(
            BRIEFING,
            "아침 브리핑",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "매일 아침 일정과 할 일 미리보기를 알립니다."
        }
        manager.createNotificationChannel(briefing)
    }
}
