package com.kosmos.app.platform.tile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.kosmos.app.MainActivity
import com.kosmos.app.platform.launch.VoiceLaunchHandler

/**
 * [VoiceTileService]
 * 빠른 설정(QS) 타일 — 탭하면 앱을 열고 채팅 도착 즉시 녹음을 시작합니다 (A3, PTT).
 *
 * ### Architecture Context
 * - **Layer**: App (Platform)
 * - **Dependencies**: 없음 — 인텐트 발사만. 권한 요청은 Activity 컨텍스트가 필요하므로
 *   녹음 시작은 ChatScreen 의 자동 시작 훅이 맡는다(타일에서 직접 녹음 불가).
 *
 * [WHY] 잠금 상태면 unlockAndRun — 마이크는 잠금 화면 너머로 열 수 없다.
 */
class VoiceTileService : TileService() {

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { launchVoiceChat() }
        } else {
            launchVoiceChat()
        }
    }

    private fun launchVoiceChat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // [WHY] API 34+ 는 Intent 오버로드가 예외를 던진다 — PendingIntent 만 허용.
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    // [WHY] 다운로드(0)·브리핑(3)·리마인더(4)와 다른 코드 — 같으면 PendingIntent 공유.
                    REQUEST_CODE,
                    voiceLaunchIntent(this),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(voiceLaunchIntent(this))
        }
    }

    private companion object {
        const val REQUEST_CODE = 5
    }
}

/**
 * 위젯 🎤·QS 타일이 공유하는 음성 진입 인텐트.
 *
 * [WHY] SINGLE_TOP — MainActivity 가 singleTop 이므로 앱이 떠 있으면 onNewIntent 로 전달돼
 * 재생성 없이 즉시 녹음이 시작된다. NEW_TASK 는 액티비티 밖(타일·위젯)에서의 시작 요건.
 */
internal fun voiceLaunchIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(VoiceLaunchHandler.ACTION_VOICE_INPUT)
        .addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
