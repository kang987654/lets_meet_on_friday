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

    const val NOTIF_ID_DOWNLOAD_PROGRESS = 1001
    const val NOTIF_ID_DOWNLOAD_RESULT = 1002

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
    }
}
